package ax.dkarlsso.game_management;

import ax.dkarlsso.game_management.features.Feature;
import ax.dkarlsso.game_management.games.Game;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.autoscaling.HealthCheck;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketAccessControl;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.constructs.Construct;

import java.util.List;

import static ax.dkarlsso.game_management.Scripts.*;

/**
 * Creates a single EC2 instance for hosting a game server.
 * Autoscaling group is used to utilize spot instances for extremely cost effecient servers
 */
@Getter
public class GameServerStack extends Stack {

    private final IBucket scriptBucket;

    private final AutoScalingGroup autoScalingGroup;

    private final UserData userData = UserData.forLinux();

    private final IRole role;

    private final Scripts scripts = new Scripts();

    private final IVpc vpc;

    private final Game game;

    public static GameServerStack create(final Construct scope,
                                  final StackProps props,
                                  final Game game,
                                  final List<Feature> features,
                                  final List<String> availabilityZones) {
        features.stream()
                .filter(f -> f instanceof Feature.DiscFeature)
                .map(f -> (Feature.DiscFeature)f)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A disc feature must be provided"));

        var gameServerStack = new GameServerStack(scope, props, game, availabilityZones);
        gameServerStack.applyFeatures(gameServerStack, features, availabilityZones);
        return gameServerStack;
    }

    private GameServerStack(@NotNull Construct scope, StackProps props, Game game, List<String> availabilityZones) {
        super(scope, game.getGameServerId(), props);
        this.vpc = Vpc.fromLookup(this, "Vpc", VpcLookupOptions.builder()
                .isDefault(true)
                .build());
        this.game = game;

        var gameServerId = game.getGameServerId();
        scriptBucket = Bucket.Builder.create(this, gameServerId + "ScriptBucket")
                .bucketName(gameServerId + "-server-scripts")
                .accessControl(BucketAccessControl.PRIVATE)
                .build();

        final SecurityGroup instanceSecurityGroup = SecurityGroup.Builder.create(this, gameServerId + "SecurityGroup")
                .vpc(vpc)
                .allowAllOutbound(true)
                .build();

        game.getPorts().forEach(port -> instanceSecurityGroup.addIngressRule(Peer.anyIpv4(), port));

        /*
            Regular pricing
            t3.medium $0.0418
            t3.large $0.0835

            Spot pricing
            r7a.medium: 0.023 $ per hour
            t3.medium: eu-north-1a: $0.020900
            t3large: 0.039 in a and b, 0.033 in c
         */
        var spotPrice = switch (game.getInstanceType().toString()) {
            case "t3.xlarge" ->  0.65;
            case "t3.large" ->  0.30;
            case "t3.medium" ->  0.40;
            default -> throw new IllegalStateException("Unconfigured spotprice for: " + game.getInstanceType().toString());
        };

        role = Role.Builder.create(this, gameServerId + "Role")
                .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                .build();

        var launchTemplate = LaunchTemplate.Builder.create(this, gameServerId + "Template")
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.XLARGE))
                .machineImage(AmazonLinuxImage.Builder.create()
                        .cpuType(AmazonLinuxCpuType.X86_64)
                        .generation(AmazonLinuxGeneration.AMAZON_LINUX_2023)
                        .build())
                .securityGroup(instanceSecurityGroup)
                .requireImdsv2(true)
                .spotOptions(LaunchTemplateSpotOptions.builder()
                        .maxPrice(spotPrice)
                        .build())
                .role(role)
                .userData(userData)
                .build();

        autoScalingGroup = AutoScalingGroup.Builder.create(this, gameServerId + "ASG")
                .autoScalingGroupName(gameServerId)
                .launchTemplate(launchTemplate)
                .vpc(vpc)
                .maxCapacity(1)
                .minCapacity(0)
                .healthCheck(HealthCheck.ec2())
                .vpcSubnets(SubnetSelection.builder()
                        .availabilityZones(availabilityZones)
                        .subnetType(SubnetType.PUBLIC)
                        .build())
                .build();
        Tags.of(autoScalingGroup).add("GameServerId", gameServerId);

        role.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore"));
        role.addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "autoscaling:UpdateAutoScalingGroup",
                        "autoscaling:SetDesiredCapacity"))
                .resources(List.of("arn:aws:autoscaling:eu-north-1:%s:autoScalingGroup:*:autoScalingGroupName/%s".formatted(this.getAccount(), gameServerId)))
                .build());
        role.addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of("s3:*"))
                .resources(List.of(scriptBucket.getBucketArn(), scriptBucket.getBucketArn() + "/*"))
                .build());
    }

    private void applyFeatures(Stack stack, List<Feature> features, List<String> availabilityZones) {
        features.forEach(feature -> feature.apply(stack, this, availabilityZones));
        game.addStartupScript(scripts);

        var checkPlayersScriptName = "check-players.sh";
        var startupScript = "startup.sh";

        BucketDeployment.Builder.create(stack, "Upload")
                .sources(List.of(
                        Source.data(checkPlayersScriptName, generateCheckPlayersScript()),
                        Source.data(ENVIRONMENT_VARIABLES_SCRIPT_NAME, this.scripts.getEnvironmentScript()),
                        Source.data(startupScript, this.scripts.getStartupScript())))
                .destinationBucket(scriptBucket)
                .build();

        userData.addCommands(
                copyShellScript(scriptBucket, startupScript, "/tmp/"),
                copyShellScript(scriptBucket, checkPlayersScriptName, "/tmp/"),
                copyShellScript(scriptBucket, ENVIRONMENT_VARIABLES_SCRIPT_NAME, ENVIRONMENT_VARIABLES_SCRIPT_FOLDER),
                "/tmp/" + startupScript
        );
    }

    private String copyShellScript(IBucket bucket, String filename, String destination) {
        String filePath = destination + filename;
        return "aws s3 cp s3://" + bucket.getBucketName() + "/%s %s && chmod +x %s".formatted(filename, filePath, filePath);
    }

    private String generateCheckPlayersScript() {
        return """
                #!/bin/bash
                                
                source %s
                                
                NUM_PLAYERS=$(npx gamedig --type %s "$IP" | jq .numplayers)
                uptime_minutes=$(awk '{print int($1)}' /proc/uptime)
                if [ "$NUM_PLAYERS" == "0" ]; then
                   echo "Server is empty"
                   if [ "$uptime_minutes" -gt 1800 ]; then
                     echo "The system has been running for more than 30 minutes and is empty, shutting down"
                     aws autoscaling update-auto-scaling-group --auto-scaling-group-name "$AUTOSCALING_GROUP" --desired-capacity 0
                   else
                     echo "The system has been up for less than 30 minutes."
                   fi
                fi
                """.formatted(ENVIRONMENT_VARIABLES_SCRIPT_PATH, game.getGameId());
    }
}

package ax.dkarlsso.game_management;

import ax.dkarlsso.game_management.features.Route53Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.route53.*;
import software.amazon.awscdk.services.route53.targets.ApiGateway;
import software.amazon.awscdk.services.route53.targets.BucketWebsiteTarget;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.constructs.Construct;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Getter
public class GameManagementStack extends Stack {
    @SneakyThrows
    public GameManagementStack(@NotNull Construct scope, String id, final StackProps props,
                               Optional<HostedZoneAttributes> hostedZoneAttributes, List<GameServerStack> gameServers) {
        super(scope, id, props);
        Optional<IHostedZone> hostedZone = hostedZoneAttributes
                .map(att -> HostedZone.fromHostedZoneAttributes(this, "HostedZone", att));
        var lambdaRole = createLambdaRole(this, gameServers);

        var configurations = gameServers.stream()
                .map(g -> new GameConfiguration(g.getGame().getServerPassword(), g.getGame().getGameServerId(),
                        g.getGame().getGameId(), g.getAutoScalingGroup().getAutoScalingGroupName()))
                .toList();

        var function = Function.Builder.create(this, "StartLambda")
                .functionName("gameserver-management-start")
                .runtime(Runtime.NODEJS_20_X)
                .code(Code.fromAsset("src/main/resources/start-lambda"))
                .memorySize(128)
                .handler("index.handler")
                .role(lambdaRole)
                .environment(Map.of("CONFIGURATIONS", new ObjectMapper().writeValueAsString(configurations)))
                .build();

        var cert = hostedZone.map(zone -> Certificate.Builder.create(this, "Certificate")
                .domainName("game-management-api." + zone.getZoneName())
                .validation(CertificateValidation.fromDns(zone))
                .build());

        var guiBucket = createWebsiteGUI(this, hostedZone, gameServers);

        final LambdaRestApi api = LambdaRestApi.Builder.create(this, "Api")
                .restApiName("game-start-api")
                .handler(function)
                .proxy(false)
                .endpointTypes(List.of(EndpointType.REGIONAL))
                .domainName(hostedZone.map(zone -> DomainNameOptions.builder()
                        .securityPolicy(SecurityPolicy.TLS_1_2)
                        .certificate(cert.orElseThrow())
                        .domainName("game-management-api." + zone.getZoneName())
                        .endpointType(EndpointType.REGIONAL)
                        .build()).orElse(null))
                .deployOptions(StageOptions.builder()
                        .stageName("start")
                        .build())
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(List.of("*"))
                        .build())
                .build();

        // TODO: Change to POST once we get a WEB GUI
        api.getRoot()
                .addResource("start")
                .addMethod("GET", new LambdaIntegration(function));

        hostedZone.ifPresent(zone -> RecordSet.Builder.create(this, "Record")
                .recordName("game-management-api." + zone.getZoneName())
                .recordType(RecordType.A)
                .zone(zone)
                .ttl(Duration.hours(1))
                .target(RecordTarget.fromAlias(new ApiGateway(api)))
                .build());

        CfnOutput.Builder.create(this, "ApiURL")
                .value(api.getUrl())
                .build();
    }

    private Role createLambdaRole(@NotNull Stack stack, List<GameServerStack> gameServers) {
        var lambdaRole =  Role.Builder.create(stack, "gameserver-management-role")
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromManagedPolicyArn(stack, "gameserver-management-apiPolicy",
                                "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole")))
                .build();
        lambdaRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "ec2:DescribeInstances"))
                .resources(List.of("*"))
                .build());

        lambdaRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "autoscaling:UpdateAutoScalingGroup",
                        "autoscaling:SetDesiredCapacity"))
                .resources(gameServers.stream().map(a -> a.getAutoScalingGroup().getAutoScalingGroupArn()).toList())
                .build());
        return lambdaRole;
    }

    @SneakyThrows
    private Bucket createWebsiteGUI(@NotNull Stack stack, Optional<IHostedZone> zone, List<GameServerStack> gameServers) {
        var bucket = Bucket.Builder.create(this, "GuiBucket")
                .bucketName("game-management." + zone.map(IHostedZone::getZoneName).orElse(""))
                .websiteIndexDocument("index.html")
                .publicReadAccess(true)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ACLS)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build();

        zone.ifPresent(iHostedZone -> ARecord.Builder.create(this, "BucketRecord")
                .zone(iHostedZone)
                .target(RecordTarget.fromAlias(new BucketWebsiteTarget(bucket)))
                .recordName(bucket.getBucketName())
                .build());

        var gameInfo = gameServers.stream()
                .map(gameServerStack -> new GameInformation(gameServerStack.getGame().getGameServerId(),
                        gameServerStack.getFeature(Route53Feature.class).map(Route53Feature::getDomainName).orElse(null),
                        gameServerStack.getGame().getLogo(), gameServerStack.getGame().getGameServerId()))
                .toList();

        BucketDeployment.Builder.create(stack, "Upload")
                .sources(List.of(
                        Source.data("games.json", new ObjectMapper().writeValueAsString(gameInfo)),
                Source.asset("src/main/resources/gui")))
                .destinationBucket(bucket)
                .retainOnDelete(false)
                .build();
        return bucket;
    }

    /**
     * Configuration JSON uploaded to config of API gateway
     */
    private record GameConfiguration(String password, String gameServerId, String gameId, String autoscalingGroupName) {

    }

    /**
     * Server info published to public S3 bucket to display available game servers
     */
    private record GameInformation(String id, String hostname, URI logo, String name) {

    }
}
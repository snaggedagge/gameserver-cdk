package ax.dkarlsso.game_management;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
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
import software.constructs.Construct;

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
                .domainName("game-management." + zone.getZoneName())
                .validation(CertificateValidation.fromDns(zone))
                .build());

        final LambdaRestApi api = LambdaRestApi.Builder.create(this, "Api")
                .restApiName("valheim-start-api")
                .handler(function)
                .proxy(false)
                .endpointTypes(List.of(EndpointType.REGIONAL))
                .domainName(hostedZone.map(zone -> DomainNameOptions.builder()
                        .securityPolicy(SecurityPolicy.TLS_1_2)
                        .certificate(cert.orElseThrow())
                        .domainName("game-management." + zone.getZoneName())
                        .endpointType(EndpointType.REGIONAL)
                        .build()).orElse(null))
                .deployOptions(StageOptions.builder()
                        .stageName("start")
                        .build())
                .build();

        // TODO: Change to POST once we get a WEB GUI
        api.getRoot()
                .addResource("start")
                .addMethod("GET", new LambdaIntegration(function));

        hostedZone.ifPresent(zone -> RecordSet.Builder.create(this, "Record")
                .recordName("game-management." + zone.getZoneName())
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

    private record GameConfiguration(String password, String gameServerId, String gameId, String autoscalingGroupName) {

    }
}
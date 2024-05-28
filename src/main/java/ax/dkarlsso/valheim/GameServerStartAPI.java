package ax.dkarlsso.valheim;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.autoscaling.HealthCheck;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.efs.FileSystem;
import software.amazon.awscdk.services.efs.PerformanceMode;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.route53.*;
import software.amazon.awscdk.services.route53.targets.ApiGateway;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketAccessControl;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

@Getter
public class GameServerStartAPI {

    public GameServerStartAPI(@NotNull Construct scope, final String id, IHostedZone hostedZone, EFSAutoscalingGroup efsAutoscalingGroup) {
        var lambdaRole = Role.Builder.create(scope, id + "-role")
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromManagedPolicyArn(scope, id + "-apiPolicy",
                                "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole")))
                .build();
        lambdaRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "ec2:DescribeInstances",
                        "autoscaling:UpdateAutoScalingGroup",
                        "autoscaling:SetDesiredCapacity"))
                .resources(List.of("*"))
                .build());

        var function = Function.Builder.create(scope, "StartLambda")
                .functionName(id)
                .runtime(Runtime.NODEJS_20_X)
                .code(Code.fromAsset("src/main/resources/start-lambda"))
                .memorySize(128)
                .handler("index.handler")
                .role(lambdaRole)
                .environment(Map.of("AUTOSCALING_GROUP_NAME", efsAutoscalingGroup.getAutoScalingGroup().getAutoScalingGroupName()))
                .build();

        var cert = Certificate.Builder.create(scope, "Certificate")
                .domainName("valheim-management.dkarlsso.com")
                .validation(CertificateValidation.fromDns(hostedZone))
                .build();

        final LambdaRestApi api = LambdaRestApi.Builder.create(scope, "Api")
                .restApiName("valheim-start-api")
                .handler(function)
                .proxy(false)
                .endpointTypes(List.of(EndpointType.REGIONAL))
                .domainName(DomainNameOptions.builder()
                        .securityPolicy(SecurityPolicy.TLS_1_2)
                        .certificate(cert)
                        .domainName("valheim-management.dkarlsso.com")
                        .endpointType(EndpointType.REGIONAL)
                        .build())
                .deployOptions(StageOptions.builder()
                        .stageName("start")
                        .build())
                .build();

        api.getRoot()
                .addMethod("GET", new LambdaIntegration(function));

        api.getRoot()
                .addResource("start")
                .addMethod("GET", new LambdaIntegration(function));

        RecordSet.Builder.create(scope, "Record")
                .recordName("valheim-management.dkarlsso.com")
                .recordType(RecordType.A)
                .zone(hostedZone)
                .ttl(Duration.hours(1))
                .target(RecordTarget.fromAlias(new ApiGateway(api)))
                .build();

        CfnOutput.Builder.create(scope, "ApiURL")
                .value(api.getUrl())
                .build();
    }

}

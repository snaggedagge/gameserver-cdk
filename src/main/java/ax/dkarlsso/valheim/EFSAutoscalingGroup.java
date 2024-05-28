package ax.dkarlsso.valheim;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.autoscaling.HealthCheck;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.efs.FileSystem;
import software.amazon.awscdk.services.efs.PerformanceMode;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketAccessControl;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.constructs.Construct;

import java.util.List;

@Getter
public class EFSAutoscalingGroup {

    private final IBucket scriptBucket;

    private final AutoScalingGroup autoScalingGroup;

    public EFSAutoscalingGroup(@NotNull Construct scope, final String id) {
        final IVpc vpc = Vpc.fromLookup(scope, "MyVpc", VpcLookupOptions.builder()
                .isDefault(true)
                .build());

        scriptBucket = Bucket.Builder.create(scope, "ScriptBucket")
                .bucketName("dags-valheim-server-scripts")
                .accessControl(BucketAccessControl.PRIVATE)
                .build();


        final var subnet = vpc.getPublicSubnets().get(0);
        final SecurityGroup instanceSecurityGroup = SecurityGroup.Builder.create(scope, id + "SecurityGroup")
                .vpc(vpc)
                .allowAllOutbound(true)
                .build();
        instanceSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.udpRange(2456, 2458));
        instanceSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(80));
        instanceSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(9001));
        final AmazonLinuxImage linuxImage = AmazonLinuxImage.Builder.create()
                .cpuType(AmazonLinuxCpuType.X86_64)
                .generation(AmazonLinuxGeneration.AMAZON_LINUX_2023)
                .build();

        // InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MEDIUM)
        // InstanceType.of(InstanceClass.R7A, InstanceSize.MEDIUM)
        autoScalingGroup = AutoScalingGroup.Builder.create(scope, "ASG")
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.LARGE))
                /*
                    Regular pricing
                    t3.medium $0.0418
                    t3.large $0.0835

                    Spot pricing
                    r7a.medium: 0.023 $ per hour
                    t3.medium: eu-north-1a: $0.020900
                    t3large: 0.039 in a and b, 0.033 in c
                 */
                .spotPrice("0.045")
                .vpc(vpc)
                .machineImage(linuxImage)
                .maxCapacity(1)
                .minCapacity(0)
                .desiredCapacity(0)
                .healthCheck(HealthCheck.ec2())
                .securityGroup(instanceSecurityGroup)
                .vpcSubnets(SubnetSelection.builder()
                        .subnets(vpc.getPublicSubnets())
                        .onePerAz(true)
                        .build())
                .requireImdsv2(true)
                .build();

        var fileSystemName = "dags-valheim-filesystem";
        var fileSystem = FileSystem.Builder.create(scope, "Efs")
                .vpc(vpc)
                .fileSystemName(fileSystemName)
                .vpcSubnets(SubnetSelection.builder()
                        .subnets(vpc.getPublicSubnets())
                        .onePerAz(true)
                        .build())
                .enableAutomaticBackups(false)
                .encrypted(false)
                .performanceMode(PerformanceMode.GENERAL_PURPOSE)
                .build();
        fileSystem.getConnections().allowDefaultPortFrom(autoScalingGroup);

        autoScalingGroup.addUserData(
                "yum install -y amazon-efs-utils",
                "mkdir /efs",
                "mount -t efs -o tls " + fileSystem.getFileSystemId() + ":/ /efs",
                "chmod -R 777 /efs",
                copyShellScript(scriptBucket, "startup.sh", "/tmp/"),
                copyShellScript(scriptBucket, "check-players.sh", "/tmp/"),
                copyShellScript(scriptBucket, "valheim-environment.sh", "/etc/profile.d/"),
                "/tmp/startup.sh"
        );

        autoScalingGroup.getRole().addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore"));
        autoScalingGroup.getRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of("route53:ChangeResourceRecordSets"))
                .resources(List.of("arn:aws:route53:::hostedzone/Z2URP9ATDI0RG5"))
                .build());

        autoScalingGroup.getRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "autoscaling:UpdateAutoScalingGroup",
                        "autoscaling:SetDesiredCapacity",
                        "s3:*"))
                .resources(List.of("*"))
                .build());

        BucketDeployment.Builder.create(scope, "Upload")
                .sources(List.of(
                        Source.asset("src/main/resources/scripts"),
                        Source.data("valheim-environment.sh",
                                """
                                #!/bin/sh
                                export AUTOSCALING_GROUP="%s"
                                """.formatted(autoScalingGroup.getAutoScalingGroupName()))))
                .destinationBucket(scriptBucket)
                .build();
    }

    private String copyShellScript(IBucket bucket, String filename, String destination) {
        String filePath = destination + filename;
        return "aws s3 cp s3://" + bucket.getBucketName() + "/%s %s && chmod +x %s".formatted(filename, filePath, filePath);
    }
}

package ax.dkarlsso.valheim;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Size;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.autoscaling.HealthCheck;
import software.amazon.awscdk.services.ec2.*;
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
public class EBSAutoscalingGroup {

    private final IBucket scriptBucket;

    private final AutoScalingGroup autoScalingGroup;

    public EBSAutoscalingGroup(@NotNull Construct scope, final String id, Game game) {
        final IVpc vpc = Vpc.fromLookup(scope, "MyVpc", VpcLookupOptions.builder()
                .isDefault(true)
                .build());

        scriptBucket = Bucket.Builder.create(scope, "ScriptBucket")
                .bucketName("dags-enshrouded-server-scripts")
                .accessControl(BucketAccessControl.PRIVATE)
                .build();

        final SecurityGroup instanceSecurityGroup = SecurityGroup.Builder.create(scope, id + "SecurityGroup")
                .vpc(vpc)
                .allowAllOutbound(true)
                .build();

        game.getPorts().forEach(port -> instanceSecurityGroup.addIngressRule(Peer.anyIpv4(), port));
        final AmazonLinuxImage linuxImage = AmazonLinuxImage.Builder.create()
                .cpuType(AmazonLinuxCpuType.X86_64)
                .generation(AmazonLinuxGeneration.AMAZON_LINUX_2023)
                .build();

        // InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MEDIUM)
        // InstanceType.of(InstanceClass.R7A, InstanceSize.MEDIUM)
        autoScalingGroup = AutoScalingGroup.Builder.create(scope, "ASG")
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.XLARGE))
                /*
                    Regular pricing
                    t3.medium $0.0418
                    t3.large $0.0835

                    Spot pricing
                    r7a.medium: 0.023 $ per hour
                    t3.medium: eu-north-1a: $0.020900
                    t3large: 0.039 in a and b, 0.033 in c
                 */
                .spotPrice("0.065")
                .vpc(vpc)
                .machineImage(linuxImage)
                .maxCapacity(1)
                .minCapacity(0)
                .desiredCapacity(0)
                .healthCheck(HealthCheck.ec2())
                .securityGroup(instanceSecurityGroup)
                .vpcSubnets(SubnetSelection.builder()
                        .availabilityZones(List.of("eu-north-1c"))
                        .subnetType(SubnetType.PUBLIC)
                        .build())
                .requireImdsv2(true)
                .build();

        var volume = Volume.Builder.create(scope, "EbsVolume")
                .availabilityZone("eu-north-1c")
                .autoEnableIo(true)
                .encrypted(false)
                .removalPolicy(RemovalPolicy.DESTROY)
                .volumeType(EbsDeviceVolumeType.GP3)
                .size(Size.gibibytes(40))
                .build();

        // https://medium.com/@mudasirhaji/step-by-step-process-of-how-to-add-and-mount-ebs-volume-on-ubuntu-ec2-linux-instance-a4be8870a4dd

        autoScalingGroup.addUserData(
                copyShellScript(scriptBucket, "enshrouded-startup.sh", "/tmp/"),
                copyShellScript(scriptBucket, "check-players.sh", "/tmp/"),
                copyShellScript(scriptBucket, "environment-variables.sh", "/etc/profile.d/"),
                "/tmp/enshrouded-startup.sh"
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
                        "ec2:AttachVolume",
                        "s3:*"))
                .resources(List.of("*"))
                .build());

        BucketDeployment.Builder.create(scope, "Upload")
                .sources(List.of(
                        Source.asset("src/main/resources/scripts"),
                        Source.data("environment-variables.sh",
                                """
                                #!/bin/sh
                                export AUTOSCALING_GROUP="%s"
                                export VOLUME_ID="%s"
                                """.formatted(autoScalingGroup.getAutoScalingGroupName(), volume.getVolumeId()))))
                .destinationBucket(scriptBucket)
                .build();
        //// TODO: Docker container does not start weell, disc problem or bad container?
        // https://groups.google.com/g/docker-dev/c/EBXrVTv8zHs?pli=1
    }

    private String copyShellScript(IBucket bucket, String filename, String destination) {
        String filePath = destination + filename;
        return "aws s3 cp s3://" + bucket.getBucketName() + "/%s %s && chmod +x %s".formatted(filename, filePath, filePath);
    }
}

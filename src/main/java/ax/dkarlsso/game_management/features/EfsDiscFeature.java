package ax.dkarlsso.game_management.features;

import ax.dkarlsso.game_management.GameServerStack;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.efs.FileSystem;
import software.amazon.awscdk.services.efs.PerformanceMode;

import java.util.List;

/**
 * EFS disc feature, which creates a multi-AZ capable file system. EFS disc costs a bit more than EBS,
 * so this is not recommended for servers requiring large amounts of disc
 */
public class EfsDiscFeature implements Feature.DiscFeature {

    @Override
    public void apply(@NotNull Stack stack, GameServerStack gameServerStack, List<String> availabilityZones) {
        var fileSystemName = gameServerStack.getGame().getGameServerId() + "-filesystem";
        var fileSystem = FileSystem.Builder.create(stack, "Efs")
                .vpc(gameServerStack.getVpc())
                .fileSystemName(fileSystemName)
                .vpcSubnets(SubnetSelection.builder()
                        .subnets(gameServerStack.getVpc().getPublicSubnets())
                        .availabilityZones(availabilityZones)
                        .build())
                .enableAutomaticBackups(false)
                .encrypted(false)
                .performanceMode(PerformanceMode.GENERAL_PURPOSE)
                .build();
        Tags.of(fileSystem).add("GameServerId", gameServerStack.getGame().getGameServerId());
        fileSystem.getConnections().allowDefaultPortFrom(gameServerStack.getAutoScalingGroup());

        gameServerStack.getScripts().addToEnvironmentScript("""
            export EFS_ID="%s"
            """.formatted(fileSystem.getFileSystemId())
        );

        // Attach volume to EC2 and mount it
        gameServerStack.getScripts().addToStartupScript("""
                yum install -y amazon-efs-utils
                mkdir -p /mnt/game
                mount -t efs -o tls "${EFS_ID}:/" /mnt/game
                chmod -R 777 /mnt/game
                """);
    }
}

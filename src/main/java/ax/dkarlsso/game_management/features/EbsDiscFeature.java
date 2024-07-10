package ax.dkarlsso.game_management.features;

import ax.dkarlsso.game_management.GameServerStack;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.EbsDeviceVolumeType;
import software.amazon.awscdk.services.ec2.Volume;
import software.amazon.awscdk.services.iam.PolicyStatement;

import java.util.List;
import java.util.Optional;

/**
 * Feature that creates a EBS Volume and attaches it to instance.
 * EBS volumes can only be created in a single AZ, so this limits the server to only be used in a single AZ.
 * Zone C is usually cheapest for spot instances
 *
 * If multiple zones are required, or you want spot instances to be created in cheapest AZ, look into {@link EfsDiscFeature} instead.
 */
@AllArgsConstructor
public class EbsDiscFeature implements Feature.DiscFeature {
    private final Size discSize;

    @Override
    public void apply(@NotNull Stack stack, GameServerStack gameServerStack, List<String> availabilityZones) {
        if (availabilityZones.size() > 1) {
            throw new IllegalArgumentException("Can not use EBS storage with multiple availability zones");
        }
        var volume = Volume.Builder.create(stack, "EbsVolume")
                .availabilityZone(availabilityZones.getFirst())
                .autoEnableIo(true)
                .encrypted(false)
                .removalPolicy(RemovalPolicy.DESTROY)
                .volumeType(EbsDeviceVolumeType.GP3)
                .size(discSize)
                .build();
        Tags.of(volume).add("GameServerId", gameServerStack.getGame().getGameServerId());
        // Add common variables to environment
        gameServerStack.getScripts().addToEnvironmentScript("""
            export VOLUME_ID="%s"
            export INSTANCE_ID=$(curl -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/instance-id)
            """.formatted(volume.getVolumeId())
        );

        // Attach volume to EC2 and mount it
        gameServerStack.getScripts().addToStartupScript("""
                DEVICE="/dev/sdf"
                aws ec2 attach-volume --volume-id "$VOLUME_ID" --instance-id "$INSTANCE_ID" --device "$DEVICE"
                sleep 5
                mkdir -p /mnt/game
                                
                if sudo blkid "$DEVICE"; then
                     echo "A filesystem already exists on $DEVICE. Skipping mkfs."
                else
                     echo "Creating a filesystem on $DEVICE."
                     mkfs -t ext4 /dev/sdf
                fi

                mount /dev/sdf /mnt/game
                chmod -R 777 /mnt/game
                """);

        gameServerStack.getRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "ec2:AttachVolume"))
                .resources(List.of(
                        "arn:aws:ec2:*:%s:instance/*".formatted(stack.getAccount()),
                        "arn:aws:ec2:%s:%s:volume/%s".formatted(stack.getRegion(), stack.getAccount(), volume.getVolumeId())))
                .build());

    }
}

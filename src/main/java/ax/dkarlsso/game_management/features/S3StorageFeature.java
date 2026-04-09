package ax.dkarlsso.game_management.features;

import ax.dkarlsso.game_management.GameServerStack;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketAccessControl;

import java.util.List;

/**
 * Feature that creates a EBS Volume and attaches it to instance.
 * EBS volumes can only be created in a single AZ, so this limits the server to only be used in a single AZ.
 * Zone C is usually cheapest for spot instances
 *
 * If multiple zones are required, or you want spot instances to be created in cheapest AZ, look into {@link EfsDiscFeature} instead.
 */
@AllArgsConstructor
public class S3StorageFeature implements Feature.DiscFeature {

    @Override
    public void apply(@NotNull Stack stack, GameServerStack gameServerStack, List<String> availabilityZones) {
        var bucket = Bucket.Builder.create(stack, "StorageBucket")
                .bucketName(stack.getAccount() + "-" + gameServerStack.getGame().getGameServerId() + "-storage")
                .removalPolicy(RemovalPolicy.RETAIN)
                .accessControl(BucketAccessControl.PRIVATE)
                .build();
        Tags.of(bucket).add("GameServerId", gameServerStack.getGame().getGameServerId());

        gameServerStack.getScripts().addToStartupScript("""
                mkdir -p /mnt/game
                chmod -R 777 /mnt/game
                aws s3 sync s3://%s /mnt/game
                
                cat > /tmp/sync-to-s3.sh << EOF
                #!/bin/bash
                %s
                EOF
                chmod +x /tmp/sync-to-s3.sh
                (sleep 600 && (crontab -l 2>/dev/null; echo "*/5 * * * * /tmp/sync-to-s3.sh") | crontab -) &
                """.formatted(bucket.getBucketName(), gameServerStack.getGame().getSyncToS3Command(bucket.getBucketName())));

        gameServerStack.getScripts().addToShutdownScript("/tmp/sync-to-s3.sh");

        gameServerStack.getRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "s3:ListBucket",
                        "s3:GetObject",
                        "s3:PutObject",
                        "s3:DeleteObject"))
                .resources(List.of(bucket.getBucketArn()))
                .build());
    }
}

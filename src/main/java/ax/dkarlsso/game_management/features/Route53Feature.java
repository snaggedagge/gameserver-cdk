package ax.dkarlsso.game_management.features;

import ax.dkarlsso.game_management.GameServerStack;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.route53.HostedZoneAttributes;

import java.util.List;

/**
 * Route 53 feature which attaches a good looking DNS record to the game server.
 * Record is created with AWS SDK since we cant create a record to a autoscaling group,
 * and we want autoscaling group with a single instance to utilize spot instances
 */
@AllArgsConstructor
public final class Route53Feature implements Feature {

    private final HostedZoneAttributes hostedZoneAttributes;

    /** Domain name, such as valheim.dkarlsso.com */
    @Getter
    private final String domainName;

    @Override
    public void apply(@NotNull Stack stack, GameServerStack gameServerStack, List<String> availabilityZones) {
        gameServerStack.getRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of("route53:ChangeResourceRecordSets"))
                .resources(List.of("arn:aws:route53:::hostedzone/%s".formatted(hostedZoneAttributes.getHostedZoneId())))
                .build());

        gameServerStack.getScripts().addToEnvironmentScript("""
            export HOSTED_ZONE_ID="%s"
            export DOMAIN_NAME="%s"
            """.formatted(hostedZoneAttributes.getHostedZoneId(), domainName));

        // Attach volume to EC2 and mount it
        gameServerStack.getScripts().addToStartupScript("""
                cd /tmp
                cat > change-batch.json << EOF
                {
                   "Comment": "Add record to the hosted zone",
                   "Changes": [
                     {
                       "Action": "UPSERT",
                       "ResourceRecordSet": {
                         "Name": "$DOMAIN_NAME",
                         "Type": "A",
                         "TTL": 60,
                         "ResourceRecords": [
                           {
                             "Value": "$IP"
                           }
                         ]
                       }
                     }
                   ]
                }
                EOF
                
                aws route53 change-resource-record-sets --hosted-zone-id $HOSTED_ZONE_ID --change-batch file://change-batch.json
                """);
    }
}

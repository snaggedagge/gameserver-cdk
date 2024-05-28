package ax.dkarlsso.valheim;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneAttributes;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;

public class ValheimServerStack extends Stack {
    private final IHostedZone hostedZone = HostedZone.fromHostedZoneAttributes(this, "HostedZone", HostedZoneAttributes.builder()
            .hostedZoneId("Z2URP9ATDI0RG5")
            .zoneName("dkarlsso.com")
            .build());
    public ValheimServerStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // TODO: Should extend Construct and use own ID. Cant change now since that would tear down existing infra completely
        var efsAutoscalingGroup = new EFSAutoscalingGroup(this, id);
        new GameServerStartAPI(this, id, hostedZone, efsAutoscalingGroup);
    }
}
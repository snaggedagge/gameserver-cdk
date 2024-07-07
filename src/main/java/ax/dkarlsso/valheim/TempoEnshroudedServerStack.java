package ax.dkarlsso.valheim;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneAttributes;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;

public class TempoEnshroudedServerStack extends Stack {
    public TempoEnshroudedServerStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        var ebsAutoscalingGroup = new EBSAutoscalingGroup(this, id, Game.ENSHROUDED);
    }
}
package ax.dkarlsso.game_management.games;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import software.amazon.awscdk.services.ec2.InstanceType;

@SuperBuilder
@Getter
public abstract class AbstractGame implements Game {

    protected final String serverName;
    protected final String serverPassword;
    protected final String gameServerId;
    protected final InstanceType instanceType;
}

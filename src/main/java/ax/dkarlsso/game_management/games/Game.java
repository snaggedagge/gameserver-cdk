package ax.dkarlsso.game_management.games;

import ax.dkarlsso.game_management.Scripts;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Port;

import java.util.List;

public interface Game {
    String getGameId();
    List<Port> getPorts();

    String getGameServerId();

    String getServerPassword();

    InstanceType getInstanceType();

    void addStartupScript(Scripts scripts);
}

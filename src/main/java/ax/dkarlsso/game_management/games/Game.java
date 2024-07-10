package ax.dkarlsso.game_management.games;

import ax.dkarlsso.game_management.Scripts;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Port;

import java.net.URI;
import java.util.List;

public interface Game {
    String getGameId();
    List<Port> getPorts();

    String getGameServerId();
    URI getLogo();
    String getServerPassword();

    InstanceType getInstanceType();

    void addStartupScript(Scripts scripts);
}

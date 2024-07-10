package ax.dkarlsso.game_management.games;

import ax.dkarlsso.game_management.Scripts;
import lombok.experimental.SuperBuilder;
import software.amazon.awscdk.services.ec2.Port;

import java.util.List;

@SuperBuilder
public class Enshrouded extends AbstractGame {

    @Override
    public String getGameId() {
        return "enshrouded";
    }

    // TODO: Clean up all ports. Unknown which are needed still
    @Override
    public List<Port> getPorts() {
        return List.of(Port.udpRange(15636, 15637), Port.tcpRange(15636, 15637),
                Port.udp(27015),Port.tcp(27015), Port.udpRange(27031, 27036));
    }

    //// TODO: Docker container does not start weell, disc problem or bad container?
    // https://groups.google.com/g/docker-dev/c/EBXrVTv8zHs?pli=1
    @Override
    public void addStartupScript(Scripts scripts) {
        scripts.addToStartupScript("""
                chown -R 4711:4711 /mnt/game
                docker run -d \\
                    --name game \\
                    --cap-add=sys_nice \\
                    --stop-timeout 120 \\
                    -p 15636:15636/udp \\
                    -p 27015:27015/tcp \\
                    -p 27031-27036:27031-27036/udp \\
                    -v /mnt/game/enshrouded:/opt/enshrouded \\
                    -e SERVER_NAME="%s" \\
                    -e SERVER_PASSWORD="%s" \\
                    -e SERVER_QUERYPORT="15637" \\
                    -e UPDATE_CRON="*/30 * * * *" \\
                    -e SERVER_SLOT_COUNT="3" \\
                    -e BACKUP_MAX_COUNT="3" \\
                    -e PUID=4711 \\
                    -e PGID=4711 \\
                    mornedhels/enshrouded-server:latest
                """.formatted(serverName, serverPassword));
    }
}

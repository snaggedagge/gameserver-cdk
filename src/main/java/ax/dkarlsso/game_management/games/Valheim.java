package ax.dkarlsso.game_management.games;

import ax.dkarlsso.game_management.Scripts;
import lombok.experimental.SuperBuilder;
import software.amazon.awscdk.Size;
import software.amazon.awscdk.services.ec2.Port;

import java.net.URI;
import java.util.List;

@SuperBuilder
public class Valheim extends AbstractGame {

    @Override
    public String getGameId() {
        return "valheim";
    }

    @Override
    public List<Port> getPorts() {
        return List.of(Port.udpRange(2456, 2458), Port.tcp(80), Port.tcp(9001));
    }

    @Override
    public URI getLogo() {
        return URI.create("https://upload.wikimedia.org/wikipedia/fi/b/bf/Valheim-logo.png");
    }

    @Override
    public void addStartupScript(Scripts scripts) {
        scripts.addToStartupScript("""
                # Docs https://github.com/community-valheim-tools/valheim-server-docker
                docker run -d \\
                    --name game \\
                    --cap-add=sys_nice \\
                    --stop-timeout 120 \\
                    -p 2456-2458:2456-2458/udp \\
                    -p 9001:9001/tcp \\
                    -p 80:80/tcp \\
                    -v /mnt/game/config:/config \\
                    -v /mnt/game/data:/opt/valheim \\
                    -v /mnt/game/logs:/home/valheim/Steam/logs \\
                    -e SERVER_NAME="%s" \\
                    -e WORLD_NAME="Bak i Tranvik" \\
                    -e SERVER_PASS="%s" \\
                    -e STATUS_HTTP="true" \\
                    -e SUPERVISOR_HTTP="true" \\
                    -e BEPINEX="true" \\
                    -e SUPERVISOR_HTTP_PASS="%s" \\
                    ghcr.io/community-valheim-tools/valheim-server
                # STATUS_HTTP enables a /status.json resource
                # Supervisor enabled on port 9001
                """.formatted(serverName, serverPassword, serverPassword));
    }

    @Override
    public Size getDiscSizeRequired() {
        return Size.gibibytes(12);
    }

    @Override
    public String getSyncToS3Command(final String s3BucketName) {
        return """
                if [ -d "/mnt/game/config/backups" ]; then
                  aws s3 sync /mnt/game/config/backups s3://%1$s/config/backups --delete
                fi
                if [ -d "/mnt/game/config/worlds_local" ]; then
                  aws s3 sync /mnt/game/config/worlds_local s3://%1$s/config/worlds_local --delete
                fi
                aws s3 sync /mnt/game/config s3://%1$s/config --exclude "*/bepinex.tmp/*"
                aws s3 sync /mnt/game/data s3://%1$s/data --delete --exclude "*/bepinex.tmp/*"
                """.formatted(s3BucketName);
    }
}

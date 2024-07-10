package ax.dkarlsso.game_management.features;

import ax.dkarlsso.game_management.GameServerStack;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Stack;

import java.util.List;

public interface Feature {

    void apply(@NotNull Stack stack, GameServerStack gameServerStack, List<String> availabilityZones);

    public interface DiscFeature extends Feature {

    }
}

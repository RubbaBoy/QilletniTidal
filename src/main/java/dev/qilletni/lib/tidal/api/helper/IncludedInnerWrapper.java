package dev.qilletni.lib.tidal.api.helper;

import com.tidal.sdk.tidalapi.generated.models.IncludedInner;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class IncludedInnerWrapper {

    private final Map<String, IncludedInner> includedInners;

    public IncludedInnerWrapper(@Nullable List<IncludedInner> includedInners) {
        if (includedInners == null) {
            this.includedInners = Collections.emptyMap();
        } else {
            this.includedInners = includedInners.stream().collect(Collectors.toMap(ModelHelper::getIncludedInnerId, i -> i));
        }
    }

    public <T extends IncludedInner> Optional<T> getInner(String id, Class<T> innerClass) {
        return Optional.ofNullable(includedInners.get(id))
                .filter(innerClass::isInstance)
                .map(innerClass::cast);
    }

    @Override
    public String toString() {
        return "IncludedInnerWrapper{" +
                "includedInners=" + includedInners +
                '}';
    }
}

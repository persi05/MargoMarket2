package com.margomarket.moderation;

import com.margomarket.shared.ForbiddenOperationException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class UserRestrictedException extends ForbiddenOperationException {
    public final ModerationScope scope;
    public final String reason;
    public final Instant expiresAt;

    public UserRestrictedException(ModerationScope scope, String reason, Instant expiresAt) {
        super(
                (scope == ModerationScope.ACCOUNT
                                ? "Twoje konto jest zablokowane. "
                                : scope.label + " jest zablokowane. ")
                        + "Powód: "
                        + reason);
        this.scope = scope;
        this.reason = reason;
        this.expiresAt = expiresAt;
    }

    public Map<String, Object> response() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", 403);
        result.put(
                "code", scope == ModerationScope.ACCOUNT ? "ACCOUNT_BLOCKED" : "ACTION_RESTRICTED");
        result.put("scope", scope);
        result.put("message", getMessage());
        result.put("reason", reason);
        result.put("expiresAt", expiresAt);
        return result;
    }
}

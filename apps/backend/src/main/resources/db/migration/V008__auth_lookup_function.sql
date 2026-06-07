-- Rota V008 — global user lookup for LOGIN (plan §8.3).
--
-- Login only has an email and must find the user's tenant before any tenant context exists.
-- RLS would hide cross-tenant rows, so this SECURITY DEFINER function runs as its owner
-- (the privileged migration role) and bypasses RLS — but exposes ONLY the four columns auth
-- needs, for an EXACT email match. It cannot enumerate users or read other data.

CREATE FUNCTION auth_lookup_user(p_email TEXT)
    RETURNS TABLE(id UUID, tenant_id UUID, password_hash TEXT, email_verified BOOLEAN)
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
    SELECT id, tenant_id, password_hash, email_verified
    FROM users
    WHERE email = p_email;
$$;

-- Lock it down: only the application role may call it.
REVOKE ALL ON FUNCTION auth_lookup_user(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_lookup_user(TEXT) TO rota_app;

"""Configurable OIDC/JWKS bearer validation. No issuer is assumed or bundled."""
from __future__ import annotations

from dataclasses import dataclass
import os
from urllib.parse import urlparse

import jwt
from jwt import PyJWKClient


def auth_disabled(env: dict[str, str] | None = None) -> bool:
    values = os.environ if env is None else env
    return values.get("STEM_STEM_CLOUD_DISABLE_AUTH", "").strip().lower() in ("1", "true", "yes", "on")


class AuthConfigurationError(RuntimeError):
    pass


class InvalidBearerToken(ValueError):
    pass


class TokenVerificationUnavailable(RuntimeError):
    """The verifier could not reach its configured key service; never accept a token."""


@dataclass(frozen=True)
class AuthSettings:
    issuer: str
    audience: str
    jwks_url: str

    @classmethod
    def from_env(cls, env: dict[str, str] | None = None) -> "AuthSettings | None":
        values = os.environ if env is None else env
        issuer = values.get("RAJTUNE_AUTH_ISSUER", "").strip()
        audience = values.get("RAJTUNE_AUTH_AUDIENCE", "").strip()
        jwks_url = values.get("RAJTUNE_AUTH_JWKS_URL", "").strip()
        if not (issuer and audience and jwks_url):
            return None
        parsed = urlparse(jwks_url)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
            raise AuthConfigurationError("JWKS URL must be an HTTPS URL without embedded credentials")
        if not issuer.startswith("https://"):
            raise AuthConfigurationError("OIDC issuer must use HTTPS")
        return cls(issuer=issuer, audience=audience, jwks_url=jwks_url)


class TokenVerifier:
    """Verifies short-lived RS256 OIDC JWTs; caller must supply configured issuer/audience."""

    def __init__(self, settings: AuthSettings | None, jwks_client: object | None = None):
        self.settings = settings
        self._jwks_client = jwks_client

    def verify(self, token: str) -> str:
        if self.settings is None:
            raise AuthConfigurationError("RajTune token issuer is not configured")
        if not token or len(token) > 16_384:
            raise InvalidBearerToken("Malformed bearer token")
        try:
            header = jwt.get_unverified_header(token)
            if header.get("alg") != "RS256":
                raise InvalidBearerToken("Unsupported token algorithm")
            client = self._jwks_client or PyJWKClient(self.settings.jwks_url, timeout=3)
            try:
                key = client.get_signing_key_from_jwt(token).key
            except jwt.PyJWKClientConnectionError as exc:
                raise TokenVerificationUnavailable("Configured JWKS service is unreachable") from exc
            claims = jwt.decode(
                token,
                key,
                algorithms=["RS256"],
                audience=self.settings.audience,
                issuer=self.settings.issuer,
                options={"require": ["exp", "sub", "iss", "aud"]},
                leeway=15,
            )
            subject = claims.get("sub")
            if not isinstance(subject, str) or not subject.strip():
                raise InvalidBearerToken("Token has no subject")
            return subject
        except (InvalidBearerToken, TokenVerificationUnavailable):
            raise
        except Exception as exc:
            raise InvalidBearerToken("Token validation failed") from exc

package model.entity;

public class OauthCredentials {
    String clientId;
    String clientSecret;
    String refreshToken;
    final String grantType = "refresh_token";

    public OauthCredentials(String clientId, String clientSecret, String refreshToken) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getGrantType() {
        return grantType;
    }
}

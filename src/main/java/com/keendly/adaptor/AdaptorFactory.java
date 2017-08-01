package com.keendly.adaptor;

import com.keendly.adaptor.inoreader.InoreaderAdaptor;
import com.keendly.adaptor.model.auth.Token;
import com.keendly.adaptor.newsblur.NewsblurAdaptor;
import com.keendly.adaptor.oldreader.OldReaderAdaptor;
import com.keendly.model.Provider;
import com.keendly.model.User;

import java.util.HashMap;
import java.util.Map;

public class AdaptorFactory {

    private static Map<Provider, Class<? extends Adaptor>> ADAPTORS = new HashMap();
    static {
        ADAPTORS.put(Provider.OLDREADER, OldReaderAdaptor.class);
        ADAPTORS.put(Provider.INOREADER, InoreaderAdaptor.class);
        ADAPTORS.put(Provider.NEWSBLUR, NewsblurAdaptor.class);
    }

    public static Adaptor getInstance(Provider provider){
        for (Provider p : ADAPTORS.keySet()){
            if (p == provider){
                try {
                    return ADAPTORS.get(p).newInstance();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        throw new RuntimeException("adaptor for " + provider.name() + " not found");
    }

    public static Adaptor getInstance(User user) {
        Token token = Token.builder()
            .accessToken(user.getAccessToken())
            .refreshToken(user.getRefreshToken())
            .build();
        return AdaptorFactory.getInstance(user.getProvider(), token);
    }

    private static Adaptor getInstance(Provider provider, Token token){
        for (Provider p : ADAPTORS.keySet()){
            if (p == provider){
                try {
                    return ADAPTORS.get(p).getConstructor(Token.class).newInstance(token);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        throw new RuntimeException("adaptor for " + provider.name() + " not found");
    }
}

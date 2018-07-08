package com.keendly.premium;

import static com.keendly.utils.ConfigUtils.*;

import com.keendly.model.Premium;
import com.keendly.model.User;
import com.stripe.Stripe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class PremiumUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PremiumUtils.class);

    static {
        Stripe.apiKey = parameter("STRIPE_KEY");
    }

    public static Premium getPremiumStatus(User user) {
        Premium.PremiumBuilder builder = Premium.builder().active(false);
        if (user.getForcePremium() != null && user.getForcePremium()) {
            builder.active(true).cancellable(false);
            return builder.build();
        }
        if (user.getPremiumSubscriptionId() != null) {
            com.stripe.model.Subscription subscription;
            try {
                subscription = com.stripe.model.Subscription.retrieve(user.getPremiumSubscriptionId());
            } catch (Exception e) {
                LOG.error("Error fetching subscription: " + user.getPremiumSubscriptionId(), e);
                return builder.active(false).build();
            }

            if ("active".equalsIgnoreCase(subscription.getStatus()) || "trialing".equalsIgnoreCase(subscription.getStatus())) {
                builder.active(true);
                if (subscription.getCancelAtPeriodEnd()) {
                    builder.cancellable(false);
                    builder.expires(new Date(subscription.getCurrentPeriodEnd() * 1000));
                } else {
                    builder.cancellable(true);
                }
            } else {
                builder.active(false);
            }

        }
        return builder.build();
    }
}

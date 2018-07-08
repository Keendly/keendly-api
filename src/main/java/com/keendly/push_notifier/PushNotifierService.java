package com.keendly.push_notifier;

import com.amazonaws.services.lambda.invoke.LambdaFunction;

public interface PushNotifierService {

    @LambdaFunction(functionName="push-notifier")
    String sendNotification(PushNotifierRequest input);
}

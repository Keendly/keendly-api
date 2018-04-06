package com.keendly.perun;

import com.amazonaws.services.lambda.invoke.LambdaFunction;

public interface PerunService {

    String TEMPLATE = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>\n"+
        "    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n"+
        "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"+
        "    <title>Keendly Delivery Issue</title>\n"+
        "    <style type=\"text/css\">\n"+
        "        #outlook a {padding:0;}\n"+
        "        body{width:100% !important; -webkit-text-size-adjust:100%; -ms-text-size-adjust:100%; margin:0; padding:0;}\n"+
        "        .ExternalClass {width:100%;}\n"+
        "        .ExternalClass, .ExternalClass p, .ExternalClass span, .ExternalClass font, .ExternalClass td, .ExternalClass div {line-height: 100%;}\n"+
        "        #backgroundTable {margin:0; padding:0; width:100% !important; line-height: 100% !important;}\n"+
        "        img {outline:none; text-decoration:none; -ms-interpolation-mode: bicubic;}\n"+
        "        a img {border:none;}\n"+
        "        .image_fix {display:block;}\n"+
        "        p {margin: 1em 0;}\n"+
        "        h1, h2, h3, h4, h5, h6 {color: black !important;}\n"+
        "        h1 a, h2 a, h3 a, h4 a, h5 a, h6 a {color: blue !important;}\n"+
        "        h1 a:active, h2 a:active,  h3 a:active, h4 a:active, h5 a:active, h6 a:active {\n"+
        "        color: red !important;\n"+
        "        }\n"+
        "\n"+
        "        h1 a:visited, h2 a:visited,  h3 a:visited, h4 a:visited, h5 a:visited, h6 a:visited {\n"+
        "        color: purple !important;\n"+
        "        }\n"+
        "        table td {border-collapse: collapse;}\n"+
        "        table { border-collapse:collapse; mso-table-lspace:0pt; mso-table-rspace:0pt; }\n"+
        "        a {color: #ec3f8c; text-decoration: none}\n"+
        "        @media only screen and (max-device-width: 480px) {\n"+
        "        a[href^=\"tel\"], a[href^=\"sms\"] {\n"+
        "        text-decoration: none;\n"+
        "        color: black; /* or whatever your want */\n"+
        "        pointer-events: none;\n"+
        "        cursor: default;\n"+
        "        }\n"+
        "\n"+
        "        .mobile_link a[href^=\"tel\"], .mobile_link a[href^=\"sms\"] {\n"+
        "        text-decoration: default;\n"+
        "        color: orange !important; /* or whatever your want */\n"+
        "        pointer-events: auto;\n"+
        "        cursor: default;\n"+
        "        }\n"+
        "        }\n"+
        "\n"+
        "        @media only screen and (min-device-width: 768px) and (max-device-width: 1024px) {\n"+
        "        a[href^=\"tel\"], a[href^=\"sms\"] {\n"+
        "        text-decoration: none;\n"+
        "        color: blue;\n"+
        "        pointer-events: none;\n"+
        "        cursor: default;\n"+
        "        }\n"+
        "\n"+
        "        .mobile_link a[href^=\"tel\"], .mobile_link a[href^=\"sms\"] {\n"+
        "        text-decoration: default;\n"+
        "        color: orange !important;\n"+
        "        pointer-events: auto;\n"+
        "        cursor: default;\n"+
        "        }\n"+
        "        }\n"+
        "\n"+
        "        @media only screen and (-webkit-min-device-pixel-ratio: 2) {\n"+
        "        /* Put your iPhone 4g styles in here */\n"+
        "        }\n"+
        "        @media only screen and (-webkit-device-pixel-ratio:.75){\n"+
        "        /* Put CSS for low density (ldpi) Android layouts in here */\n"+
        "        }\n"+
        "        @media only screen and (-webkit-device-pixel-ratio:1){\n"+
        "        /* Put CSS for medium density (mdpi) Android layouts in here */\n"+
        "        }\n"+
        "        @media only screen and (-webkit-device-pixel-ratio:1.5){\n"+
        "        /* Put CSS for high density (hdpi) Android layouts in here */\n"+
        "        }\n"+
        "\n"+
        "        .button {\n"+
        "        background-position: 14px 14px;\n"+
        "        background-repeat: no-repeat;\n"+
        "        background-size: 28px 28px;\n"+
        "        color: white;\n"+
        "        cursor: pointer;\n"+
        "        display: block;\n"+
        "        font-size: 16px;\n"+
        "        line-height: 56px;\n"+
        "        margin-top: 20px;\n"+
        "        margin-bottom: 20px;\n"+
        "        margin-left: auto;\n"+
        "        margin-right: auto;\n"+
        "        text-decoration: none;\n"+
        "        width: 244px;\n"+
        "        background-color: #39b1c6;\n"+
        "        text-align: center;\n"+
        "        }\n"+
        "    </style>\n"+
        "    <!--[if IEMobile 7]>\n"+
        "    <style type=\"text/css\">\n"+
        "        /* Targeting Windows Mobile */\n"+
        "    </style>\n"+
        "    <![endif]-->\n"+
        "    <!--[if gte mso 9]>\n"+
        "    <style>\n"+
        "        /* Target Outlook 2007 and 2010 */\n"+
        "    </style>\n"+
        "    <![endif]-->\n"+
        "</head>\n"+
        "<body>\n"+
        "<table id=\"backgroundTable\" style=\"background-color: #f2f2f2\" width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\">\n"+
        "    <tbody><tr>\n"+
        "        <td style=\"padding: 20px\">\n"+
        "            <table style=\"background-color: white\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\">\n"+
        "                <tbody><tr>\n"+
        "                    <td width=\"200\" valign=\"top\"><img class=\"image_fix\" src=\"http://keendly-rsrcs.s3-website-eu-west-1.amazonaws.com/tran.png\" alt=\"keendly logo\" title=\"logo\" style=\"padding: 20px\" width=\"564\"></td>\n"+
        "                </tr>\n"+
        "                <tr>\n"+
        "                    <td class=\"torTextContent\" style=\"padding-top: 9px;padding-right: 18px;padding-bottom: 9px;padding-left: 18px;mso-table-lspace: 0pt;mso-table-rspace: 0pt;-ms-text-size-adjust: 100%;-webkit-text-size-adjust: 100%;color: #606060;font-family: Helvetica;font-size: 15px;line-height: 150%;text-align: left;\" valign=\"top\">\n"+
        "                        Hello,\n"+
        "                        <br><br>We couldn't find any unread articles in any of the scheduled feeds: {{FEEDS}} So we skipped delivery this time. If you don't want to receive this kind of notifications, you can change it in your user settings.<br><br>\n"+
        "\n"+
        "                        <a class=\"button\" href=\"https://app.keendly.com/settings\">Go to <b>settings</b></a>\n"+
        "\n"+
        "                        <p>Yours sincerely,<br>\n"+
        "                            Keendly Team</p>\n"+
        "                        <p><a href=\"mailto:contact@keendly.com\">contact@keendly.com</a></p>\n"+
        "                    </td>\n"+
        "                </tr>\n"+
        "            </tbody></table>\n"+
        "        </td>\n"+
        "    </tr>\n"+
        "</tbody></table>\n"+
        "\n"+
        "\n"+
        "</body></html>";

    @LambdaFunction(functionName="perun_swf")
    String sendEmail(PerunRequest input);
}

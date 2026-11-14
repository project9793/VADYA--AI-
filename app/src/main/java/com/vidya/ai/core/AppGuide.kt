package com.vidya.ai.core

/** What is where in VIDYA AI, how to open it and set it up. Used by the voice/text guide and the tour. */
object AppGuide {

    class Item(
        val id: String,
        val title: String,
        val where: String,
        val route: String?,
        val aliases: List<String>,
        val steps: List<String>,
        val say: String
    )

    class Screen(val say: String)
    class TourStep(val route: String, val title: String, val say: String)

    val items = listOf(
        Item(
            "voice_chat", "बोलकर बात करना", "Home स्क्रीन: बीच का गोला या नीचे का mic बटन", "home",
            listOf("बोलकर बात", "voice chat", "mic कहाँ", "माइक कहाँ", "कैसे बोलूँ", "कैसे बोलूं", "कैसे बोलू",
                "बात कैसे करूँ", "बात कैसे करूं", "बात कैसे करू"),
            listOf("नीचे Home टैब खोलिए", "बीच के गोले या नीले mic बटन को दबाइए",
                "जब \"सुन रही हूँ\" दिखे तब बोलिए", "बोलना रुकते ही मैं जवाब देती हूँ",
                "बीच में रोकना हो तो बटन फिर दबाइए"),
            "बोलने के लिए होम स्क्रीन पर बीच के गोले या नीचे के माइक बटन को दबाइए। जब मैं सुन रही हूँ कहूँ, तब बोलिए। आप रुकेंगे तो मैं अपने आप जवाब दूँगी। बीच में रोकना हो तो बटन दोबारा दबाइए।"
        ),
        Item(
            "hands_free", "Hands-free (अपने आप सुनना)", "Home स्क्रीन: Hands-free बटन", "home",
            listOf("hands-free", "hands free", "handsfree", "हैंड्स फ्री", "हैंड्सफ्री", "अपने आप सुन"),
            listOf("Home टैब खोलिए", "Hands-free बटन दबाइए (वह रंगीन हो जाएगा)",
                "अब मेरे बोलने के बाद मैं अपने आप फिर सुनने लगूँगी"),
            "होम स्क्रीन पर Hands-free का बटन दबाइए। फिर मेरे बोलने के बाद मैं अपने आप दोबारा सुनने लगूँगी, हर बार माइक दबाने की ज़रूरत नहीं पड़ेगी।"
        ),
        Item(
            "text_chat", "लिखकर चैट करना", "Chat टैब", "chat",
            listOf("chat कहाँ", "चैट कहाँ", "लिखकर पूछ", "टाइप करके", "text chat", "नई चैट", "new chat", "नई बातचीत"),
            listOf("नीचे Chat टैब खोलिए", "नीचे लिखने की जगह में सवाल लिखकर भेजिए",
                "या बगल के mic बटन से बोलिए", "नई बातचीत के लिए ऊपर + दबाइए"),
            "नीचे चैट टैब खोलिए। नीचे लिखने की जगह में सवाल लिखकर भेजिए, या बगल का माइक बटन दबाकर बोलिए। नई बातचीत शुरू करने के लिए ऊपर प्लस का निशान दबाइए।"
        ),
        Item(
            "history", "पुरानी चैट (History)", "History टैब", "history",
            listOf("history कहाँ", "हिस्ट्री कहाँ", "history कैसे", "हिस्ट्री कैसे", "पुरानी चैट", "पुरानी बातचीत",
                "chat history", "चैट हिस्ट्री", "हिस्ट्री में", "चैट हटा", "चैट डिलीट"),
            listOf("नीचे History टैब खोलिए", "ऊपर खोज की जगह में शब्द लिखकर ढूँढिए",
                "किसी बातचीत को दबाकर खोलिए", "कूड़ेदान के निशान से हटाइए"),
            "नीचे हिस्ट्री टैब खोलिए। यहाँ पुरानी सारी बातचीत मिलती है। ऊपर खोज की जगह में शब्द लिखकर ढूँढिए, किसी बातचीत को दबाकर खोलिए, और कूड़ेदान के निशान से हटाइए।"
        ),
        Item(
            "account", "अपना नाम (खाता)", "Tools टैब → 1. खाता", "tools/account",
            listOf("खाता", "अकाउंट", "account", "नाम कैसे डालूँ", "नाम कैसे डालू", "नाम कैसे सेट", "अपना नाम कैसे",
                "यूज़र नाम", "user name", "मेरा नाम बदल"),
            listOf("नीचे Tools टैब खोलिए", "सबसे ऊपर 1. खाता दबाइए", "अपना नाम लिखिए", "Save दबाइए"),
            "नीचे टूल्स टैब खोलिए, फिर नंबर एक, खाता, दबाइए। वहाँ अपना नाम लिखकर सेव दबाइए। उसके बाद मैं आपको उसी नाम से बुलाऊँगी।"
        ),
        Item(
            "assistant_name", "मेरा (assistant का) नाम", "Tools टैब → 2. Assistant का नाम", "tools/assistant",
            listOf("assistant का नाम", "असिस्टेंट का नाम", "तुम्हारा नाम बदल", "अपना नाम बदल", "विद्या का नाम",
                "assistant name", "असिस्टेंट नेम"),
            listOf("नीचे Tools टैब खोलिए", "2. Assistant का नाम दबाइए", "नया नाम लिखिए", "Save दबाइए"),
            "टूल्स टैब में नंबर दो, असिस्टेंट का नाम, खोलिए। मेरे लिए नया नाम लिखिए और सेव दबाइए।"
        ),
        Item(
            "voice_pick", "आवाज़ बदलना / चुनना", "Tools टैब → 3. आवाज़ चुनें", "tools/voice",
            listOf("आवाज़ बदल", "आवाज बदल", "आवाज़ चुन", "आवाज चुन", "आवाज़ कैसे", "आवाज कैसे", "voice बदल",
                "voice change", "सरवम", "sarvam voice", "natural voice", "स्वाभाविक आवाज़"),
            listOf("नीचे Tools टैब खोलिए", "3. आवाज़ चुनें दबाइए",
                "Sarvam चुनिए (हिन्दी के लिए सबसे अच्छी; उसकी key पहले डालनी होगी)",
                "कोई आवाज़ चुनकर \"आवाज़ सुनकर देखें\" दबाइए"),
            "टूल्स टैब में नंबर तीन, आवाज़ चुनें, खोलिए। हिन्दी के लिए सरवम चुनिए, अगर आपने उसकी की डाली है। फिर कोई आवाज़ चुनकर, आवाज़ सुनकर देखें, दबाइए।"
        ),
        Item(
            "language", "भाषा बदलना (हिन्दी, मलयालम...)", "Tools टैब → 4. भाषा", "tools/language",
            listOf("भाषा बदल", "भाषा चुन", "भाषा कैसे", "language बदल", "language change", "language set",
                "बोलने की भाषा", "मलयालम में बोल", "तमिल में बोल"),
            listOf("नीचे Tools टैब खोलिए", "4. भाषा दबाइए", "जिस भाषा में बोलना हो उसे दबाइए"),
            "टूल्स टैब में नंबर चार, भाषा, खोलिए। जिस भाषा में बोलना हो वह दबाइए, जैसे हिन्दी, मलयालम, तमिल या अंग्रेज़ी।"
        ),
        Item(
            "memory", "Memory (याद रखना)", "Tools टैब → 5. Memory", "tools/memory",
            listOf("memory कैसे", "memory कहाँ", "मेमोरी कैसे", "मेमोरी कहाँ", "memory vault", "याद रखना",
                "याद कैसे", "याद रखो कैसे"),
            listOf("नीचे Tools टैब खोलिए", "5. Memory दबाइए", "नई बात लिखकर जोड़ें दबाइए",
                "या मुझसे बोलिए: \"याद रखो, ...\"", "हटाने के लिए कूड़ेदान दबाइए"),
            "टूल्स टैब में नंबर पाँच, मेमोरी, खोलिए। नई बात लिखकर जोड़ें दबाइए। या मुझसे सीधे बोलिए, याद रखो, और फिर वह बात। हटाने के लिए कूड़ेदान का निशान दबाइए।"
        ),
        Item(
            "api_key", "API Key डालना (Groq / Gemini / Sarvam)", "Tools टैब → 6. API Key", "tools/keys",
            listOf("api key", "एपीआई", "api की", "key कहाँ", "की कहाँ", "key कैसे", "की कैसे", "groq", "ग्रोक", "ग्रो की",
                "gemini key", "जेमिनी की", "sarvam key", "सरवम की", "key डाल", "की डाल"),
            listOf("नीचे Tools टैब खोलिए", "6. API Key दबाइए",
                "Groq: console.groq.com पर Sign in करके API Keys से key बनाइए (gsk_ से शुरू होती है)",
                "Gemini: aistudio.google.com से key बनाइए",
                "Sarvam: dashboard.sarvam.ai से key लीजिए (आवाज़ के लिए)",
                "हर key के नीचे Save दबाइए", "सबसे नीचे Test करें दबाकर जाँच लीजिए"),
            "टूल्स टैब में नंबर छह, एपीआई की, खोलिए। सबसे पहले ग्रोक की डालिए, वह तेज़ है और उसकी लिमिट बड़ी है। फिर चाहें तो जेमिनी और सरवम की भी डालिए। हर की के नीचे सेव दबाइए, और आख़िर में नीचे टेस्ट करें दबाकर जाँच लीजिए।"
        ),
        Item(
            "permissions", "Permissions (माइक, कैमरा, संपर्क, कॉल...)", "Tools टैब → 7. Permissions", "tools/permissions",
            listOf("permission", "परमिशन", "अनुमति", "allow कैसे", "mic permission", "माइक की अनुमति", "camera permission",
                "कैमरा परमिशन", "location permission", "notification permission", "contacts permission",
                "call permission", "कॉल की अनुमति", "screen permission", "स्क्रीन परमिशन"),
            listOf("नीचे Tools टैब खोलिए", "7. Permissions दबाइए",
                "हर चीज़ के आगे का switch ON कीजिए (माइक, कैमरा, Contacts, Phone calls...)",
                "या सबसे ऊपर \"सब Allow करें\" दबाइए",
                "बंद करना हो तो switch दबाइए, फ़ोन की Settings खुलेगी वहाँ \"Don't allow\" चुनिए"),
            "टूल्स टैब में नंबर सात, परमिशन, खोलिए। हर चीज़ के आगे का स्विच ऑन कीजिए, जैसे माइक, कैमरा, कॉन्टैक्ट्स और फ़ोन कॉल। या सबसे ऊपर, सब अलाउ करें, दबाइए।"
        ),
        Item(
            "phone_control", "Call, WhatsApp, YouTube, Music", "Tools टैब → 8. Phone control", "tools/phone",
            listOf("whatsapp कैसे", "व्हाट्सएप कैसे", "व्हाट्सऐप कैसे", "call कैसे", "कॉल कैसे", "फ़ोन कंट्रोल", "फोन कंट्रोल",
                "phone control", "किसी को call", "किसी को कॉल", "गाना कैसे चला", "music कैसे", "youtube कैसे", "यूट्यूब कैसे",
                "spotify कैसे", "स्पॉटिफाई कैसे", "वीडियो कैसे चला", "music player कैसे", "म्यूज़िक प्लेयर कैसे"),
            listOf("Tools → 7. Permissions में Contacts और Phone calls ON कीजिए (WhatsApp में अपने आप Send के लिए Accessibility भी)",
                "Tools → 8. Phone control में पसंदीदा Music app चुनिए और YouTube API key डालिए (वीडियो सीधे चलाने के लिए)",
                "बोलिए: \"राहुल को call करो\"  /  \"call काट दो\"  /  \"call उठाओ\"",
                "बोलिए: \"राहुल को WhatsApp करो कि ...\" (पहले स्क्रीन पर किसे और क्या दिखेगा, आप पुष्टि करेंगे)",
                "बोलिए: \"YouTube पर ... चलाओ\"  /  \"Spotify पर ... चलाओ\"  /  \"गाना रोको\"  /  \"अगला गाना\""),
            "कॉल, व्हाट्सऐप, यूट्यूब और म्यूज़िक के लिए पहले टूल्स में परमिशन खोलकर कॉन्टैक्ट्स और फ़ोन कॉल चालू कीजिए। फिर टूल्स में नंबर आठ, फ़ोन कंट्रोल, खोलकर पसंदीदा म्यूज़िक ऐप चुनिए और यूट्यूब की डालिए। फिर मुझसे बोलिए, राहुल को कॉल करो, या राहुल को व्हाट्सऐप करो कि मैं देर से आऊँगा। मैं पहले स्क्रीन पर दिखाऊँगी कि किसे और क्या भेज रही हूँ, आप पुष्टि करेंगे।"
        ),
        Item(
            "internet_search", "इंटरनेट से ताज़ा जानकारी", "Settings टैब → इंटरनेट से ताज़ा जानकारी (switch)", "settings",
            listOf("इंटरनेट खोज", "internet search", "search कैसे", "ताज़ा खबर कैसे", "live जानकारी", "इंटरनेट से कैसे"),
            listOf("नीचे Settings टैब खोलिए", "\"इंटरनेट से ताज़ा जानकारी\" का switch ON रखिए",
                "फिर खबर, भाव, मौसम जैसे सवाल पूछिए"),
            "सेटिंग्स टैब में, इंटरनेट से ताज़ा जानकारी, का स्विच है। उसे ऑन रखिए। फिर ताज़ा खबर, भाव या मौसम पूछिए, मैं इंटरनेट से देखकर बताऊँगी।"
        ),
        Item(
            "settings_tab", "Settings टैब", "नीचे Settings टैब", "settings",
            listOf("settings कहाँ", "सेटिंग कहाँ", "सेटिंग्स कहाँ", "सेटिंग्स में क्या", "voice engine", "सुनने का तरीका",
                "listening mode"),
            listOf("नीचे Settings टैब खोलिए", "यहाँ: Mic की अनुमति, सुनने का तरीका, इंटरनेट खोज switch",
                "नाम, आवाज़, भाषा, Memory, key, Permissions → Tools टैब में हैं"),
            "सेटिंग्स टैब में माइक की अनुमति, सुनने का तरीका और इंटरनेट खोज का स्विच है। नाम, आवाज़, भाषा, मेमोरी और की जैसी बाकी चीज़ें टूल्स टैब में हैं।"
        ),
        Item(
            "test", "जवाब / आवाज़ न आए तो जाँच (Test)", "Tools टैब → 6. API Key → सबसे नीचे Test करें", "tools/keys",
            listOf("connection test", "test कहाँ", "टेस्ट कहाँ", "जाँच कहाँ", "जवाब नहीं आ", "आवाज़ नहीं आ", "आवाज नहीं आ",
                "काम नहीं कर रहा", "error आ रहा", "एरर आ रहा"),
            listOf("नीचे Tools टैब खोलिए", "6. API Key दबाइए", "सबसे नीचे \"Test करें\" दबाइए",
                "जहाँ ✖ दिखे वही सेवा गड़बड़ है; साथ में समय भी दिखेगा"),
            "जवाब या आवाज़ न आए तो टूल्स में एपीआई की खोलिए और सबसे नीचे, टेस्ट करें, दबाइए। वह बताएगा कि कौन सी सेवा में गड़बड़ है और कितना समय लगा।"
        ),
        Item(
            "tools_hub", "Tools टैब", "नीचे Tools टैब", "tools",
            listOf("tools कहाँ", "टूल्स कहाँ", "टूल्स में क्या", "tools में क्या"),
            listOf("नीचे Tools टैब दबाइए", "यहाँ 7 चीज़ें हैं: खाता, Assistant का नाम, आवाज़, भाषा, Memory, API Key, Permissions"),
            "नीचे टूल्स टैब खोलिए। वहाँ सात चीज़ें हैं: खाता, असिस्टेंट का नाम, आवाज़, भाषा, मेमोरी, एपीआई की और परमिशन।"
        )
    )

    private val screens = mapOf(
        "home" to Screen("नमस्ते! मैं विद्या हूँ। बीच के गोले या माइक बटन को दबाकर बोलिए, मैं सुनकर जवाब दूँगी। पूरा ऐप समझना हो तो मुझसे कहिए, ऐप घुमाओ। कुछ भी खोजना हो तो पूछिए, कहाँ है या कैसे करें, मैं बताऊँगी और वहाँ ले भी चलूँगी।"),
        "chat" to Screen("यह चैट है। नीचे लिखकर या माइक से पूछिए। जवाब के नीचे कॉपी, सुनो और दोबारा बनाओ के बटन हैं।"),
        "history" to Screen("यह हिस्ट्री है। यहाँ पुरानी बातचीत मिलती है। ऊपर खोजिए, किसी को दबाकर खोलिए, या कूड़ेदान से हटाइए।"),
        "tools" to Screen("यह टूल्स है। यहाँ सात चीज़ें हैं। सबसे पहले छठी, एपीआई की, डाल लीजिए, उसके बिना मैं जवाब नहीं दे पाऊँगी।"),
        "tools/guide" to Screen("यह गाइड है। यहाँ से पूरा ऐप सीख सकते हैं। किसी भी विषय पर सुनो दबाइए, मैं बोलकर समझाऊँगी, और वहाँ ले चलो दबाइए, मैं सीधे सही जगह पहुँचा दूँगी।"),
        "tools/account" to Screen("यहाँ अपना नाम लिखकर सेव दबाइए। मैं आपको उसी नाम से बुलाऊँगी।"),
        "tools/assistant" to Screen("यहाँ आप मेरा नाम बदल सकते हैं।"),
        "tools/voice" to Screen("यहाँ मेरी आवाज़ चुनिए और सुनकर देखिए। हिन्दी के लिए सरवम सबसे अच्छी है।"),
        "tools/language" to Screen("यहाँ चुनिए कि आप किस भाषा में बोलेंगे।"),
        "tools/memory" to Screen("यहाँ वे बातें हैं जो मैं याद रखती हूँ। नई बात जोड़ सकते हैं या हटा सकते हैं।"),
        "tools/keys" to Screen("यहाँ ग्रोक, जेमिनी और सरवम की की डालिए। हर की के नीचे सेव दबाइए, और आख़िर में टेस्ट करें दबाइए।"),
        "tools/permissions" to Screen("यहाँ हर अनुमति को स्विच से चालू या बंद करते हैं। कॉल और व्हाट्सऐप के लिए कॉन्टैक्ट्स और फ़ोन कॉल चालू कीजिए।"),
        "tools/phone" to Screen("यहाँ कॉल, व्हाट्सऐप, यूट्यूब और म्यूज़िक की सेटिंग है। पहले अनुमतियाँ देखिए, फिर पसंदीदा म्यूज़िक ऐप चुनिए। वीडियो सीधे चलाने के लिए यूट्यूब की डालिए।"),
        "settings" to Screen("यह सेटिंग्स है। यहाँ माइक की अनुमति, सुनने का तरीका और इंटरनेट खोज का स्विच है। नाम, आवाज़, भाषा और की जैसी बाकी चीज़ें टूल्स टैब में हैं।")
    )

    fun screen(route: String): Screen? = screens[route]
    fun byId(id: String): Item? = items.firstOrNull { it.id == id }

    val tour: List<TourStep> = listOf(
        TourStep("home", "Home", screens.getValue("home").say),
        TourStep("chat", "Chat", screens.getValue("chat").say),
        TourStep("history", "History", screens.getValue("history").say),
        TourStep("tools", "Tools", "यह टूल्स है। यहाँ सात चीज़ें हैं: खाता, असिस्टेंट का नाम, आवाज़, भाषा, मेमोरी, एपीआई की और परमिशन।"),
        TourStep("tools/keys", "API Key", "सबसे ज़रूरी है एपीआई की। ग्रोक, जेमिनी और सरवम की की यहाँ डालिए। उसके बिना मैं जवाब नहीं दे पाऊँगी।"),
        TourStep("tools/permissions", "Permissions", screens.getValue("tools/permissions").say),
        TourStep("tools/voice", "आवाज़", screens.getValue("tools/voice").say),
        TourStep("tools/language", "भाषा", screens.getValue("tools/language").say),
        TourStep("tools/memory", "Memory", screens.getValue("tools/memory").say),
        TourStep("settings", "Settings", screens.getValue("settings").say),
        TourStep("home", "Home", "बस, यही पूरा टूर था। कभी भी पूछिए, जैसे, एपीआई की कहाँ डालूँ, या आवाज़ कैसे बदलूँ। मैं बोलकर बताऊँगी और वहाँ ले भी चलूँगी।")
    )

    // ------------------------------------------------------------ understanding the question

    private val helpIntent = Regex(
        "कहाँ|कहां|कहा है|kahan|kaha|where|कैसे|kaise|how|सेट|\\bset\\b|खोलूँ|खोलूं|खोलू|खोलना|कहाँ जाऊँ|" +
            "बताओ|बताइए|समझाओ|सिखाओ|guide|गाइड|help|मदद|रास्ता|नहीं हो रहा|नहीं चल रहा|नहीं आ रहा|error|दिक्कत|समस्या",
        RegexOption.IGNORE_CASE
    )
    private val appWords = Regex("ऐप|एप|app|विद्या|vidya|tab|टैब|बटन|button|स्क्रीन|screen|यहाँ|यहां|इसमें", RegexOption.IGNORE_CASE)
    private val tourRx = Regex(
        "tour|टूर|घुमाओ|घुमा दो|ऐप दिखाओ|ऐप समझाओ|ऐप कैसे चल|पूरा गाइड|शुरू से बताओ|app guide|ऐप का गाइड|" +
            "how to use (this )?app|सब कुछ समझाओ",
        RegexOption.IGNORE_CASE
    )
    private val stopRx = Regex("^(रुको|रुक जाओ|बस|stop|रोको|बंद करो|रोक दो)", RegexOption.IGNORE_CASE)

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[?.,!।\\-:;\"']"), " ").replace(Regex("\\s+"), " ").trim()

    fun isTourRequest(msg: String): Boolean = msg.length <= 80 && tourRx.containsMatchIn(msg)
    fun isStop(msg: String): Boolean = msg.trim().length <= 20 && stopRx.containsMatchIn(msg.trim())

    /** Best matching feature for "where is X / how do I set X", or null. */
    fun match(msg: String): Item? {
        val m = normalize(msg)
        if (!helpIntent.containsMatchIn(m) && m.length > 14) return null
        var best: Item? = null
        var bestLen = 0
        for (item in items) {
            for (a in item.aliases) {
                val al = a.lowercase()
                if (m.contains(al) && al.length > bestLen) { best = item; bestLen = al.length }
            }
        }
        return best
    }

    fun looksLikeAppHelp(msg: String): Boolean {
        val m = normalize(msg)
        return helpIntent.containsMatchIn(m) && appWords.containsMatchIn(m)
    }

    /** Compact map of the app for the AI, so it can answer other app questions correctly. */
    fun mapText(): String = buildString {
        append(" APP MAP of VIDYA AI (answer app-help questions ONLY from this, in simple Hindi, naming the exact tab and button): ")
        items.forEach { append(it.title).append(" = ").append(it.where).append(". Steps: ").append(it.steps.joinToString("; ")).append(". ") }
    }
}

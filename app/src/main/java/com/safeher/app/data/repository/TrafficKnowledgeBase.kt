package com.safeher.app.data.repository

import com.safeher.app.data.model.HelplineItem
import com.safeher.app.data.model.TrafficRuleItem

object TrafficKnowledgeBase {

    val helplines = listOf(
        HelplineItem(
            id = "emergency",
            name = "National Emergency Number",
            number = "112",
            category = "emergency",
            description = "Unified single emergency helpline for Police, Fire, and Ambulance across India."
        ),
        HelplineItem(
            id = "police",
            name = "Police Control Room",
            number = "100",
            category = "police",
            description = "Direct police response line."
        ),
        HelplineItem(
            id = "ambulance_108",
            name = "Medical Emergency & Ambulance",
            number = "108",
            category = "ambulance",
            description = "Free emergency ambulance medical response service."
        ),
        HelplineItem(
            id = "women_helpline_1091",
            name = "Women in Distress Helpline",
            number = "1091",
            category = "women",
            description = "Dedicated women safety and distress response helpline."
        ),
        HelplineItem(
            id = "women_helpline_181",
            name = "National Women Helpline",
            number = "181",
            category = "women",
            description = "24x7 toll-free helpline for women affected by violence."
        ),
        HelplineItem(
            id = "road_accident",
            name = "Road Accident Emergency Service",
            number = "1073",
            category = "traffic",
            description = "Direct helpline for road accident reporting and assistance."
        ),
        HelplineItem(
            id = "highway_1033",
            name = "National Highway Emergency (NHAI)",
            number = "1033",
            category = "traffic",
            description = "Toll-free 24/7 assistance for accidents and breakdowns on National Highways."
        ),
        HelplineItem(
            id = "child_helpline",
            name = "Childline Emergency",
            number = "1098",
            category = "child",
            description = "Emergency phone outreach service for children in need of care and protection."
        )
    )

    val trafficRules = listOf(
        TrafficRuleItem(
            id = "traffic_signs",
            title = "Traffic Signs & Signals",
            rule = "Mandatory regulatory signs (e.g., STOP, GIVE WAY, NO ENTRY) must be strictly obeyed. Red light always means complete stop before the stop line.",
            penalty = "₹1,000 to ₹5,000 fine and/or suspension of driving license up to 3 months (Section 184 & 177).",
            sourceName = "Ministry of Road Transport and Highways (MoRTH)",
            sourceUrl = "https://morth.nic.in",
            iconEmoji = "🛑",
            keywords = listOf("traffic sign", "traffic signal", "red light", "stop sign", "give way", "signals", "signs")
        ),
        TrafficRuleItem(
            id = "helmet",
            title = "Two-Wheeler Helmet Regulations",
            rule = "Both driver and pillion rider aged 4+ years must wear protective headgear conforming to BIS (Bureau of Indian Standards) specifications.",
            penalty = "₹1,000 fine and disqualification of license for 3 months (Section 194D of the Motor Vehicles Act).",
            sourceName = "Motor Vehicles (Amendment) Act 2019",
            sourceUrl = "https://morth.nic.in",
            iconEmoji = "⛑️",
            keywords = listOf("helmet", "two wheeler", "bike", "pillion", "isi helmet", "bis helmet")
        ),
        TrafficRuleItem(
            id = "road_accident",
            title = "Road Accident Assistance & Good Samaritan Law",
            rule = "Call 108 (Ambulance), 1033 (National Highway), or 112 immediately. Under Section 134A of the Motor Vehicles Act, Good Samaritans who assist victims are legally protected from civil and criminal liability, police harassment, and forced witness testimony.",
            penalty = "Hit and run penalty: Up to ₹2,00,000 compensation or severe criminal imprisonment (Section 161).",
            sourceName = "Section 134A - Motor Vehicles Act",
            sourceUrl = "https://morth.nic.in",
            iconEmoji = "🚑",
            keywords = listOf("road accident", "accident", "crash", "good samaritan", "first aid", "hit and run")
        ),
        TrafficRuleItem(
            id = "seatbelt",
            title = "Seat Belt Safety Mandate",
            rule = "Drivers and all passengers occupying front and rear seats equipped with seat belts must securely wear them while moving on public roads.",
            penalty = "₹1,000 fine per passenger not wearing seat belt (Section 194B of the Motor Vehicles Act).",
            sourceName = "Central Motor Vehicle Rules (CMVR)",
            sourceUrl = "https://morth.nic.in",
            iconEmoji = "💺",
            keywords = listOf("seatbelt", "seat belt", "buckle up", "car safety")
        ),
        TrafficRuleItem(
            id = "drunk_driving",
            title = "Drunken Driving (DUI / DWI)",
            rule = "Blood alcohol content exceeding 30 mg per 100 ml of blood detected by a breath analyzer constitutes driving under the influence and is strictly illegal.",
            penalty = "First offense: fine up to ₹10,000 and/or imprisonment up to 6 months. Repeat offense within 3 years: ₹15,000 fine and/or up to 2 years imprisonment (Section 185).",
            sourceName = "Motor Vehicles Act Section 185",
            sourceUrl = "https://morth.nic.in",
            iconEmoji = "🍺",
            keywords = listOf("drunk driving", "drink and drive", "alcohol", "breath analyzer", "drunk", "liquor")
        ),
        TrafficRuleItem(
            id = "speed",
            title = "Speed Limits & Overspeeding",
            rule = "Motorists must never exceed prescribed speed limits posted along roadways. Common national highway speed limits are 70-100 km/h, city roads 40-50 km/h, and near schools/hospitals 25-30 km/h.",
            penalty = "Light motor vehicles: ₹1,000 to ₹2,000 fine. Medium/heavy vehicles: ₹2,000 to ₹4,000 with license seizure (Section 183).",
            sourceName = "MoRTH Speed Notifications",
            sourceUrl = "https://morth.nic.in",
            iconEmoji = "⚡",
            keywords = listOf("speed", "overspeeding", "speed limit", "fast driving", "radar")
        ),
        TrafficRuleItem(
            id = "mobile_phone",
            title = "Use of Mobile Phones While Driving",
            rule = "Holding or using hand-held mobile devices or communication handsets while operating a motor vehicle is strictly prohibited as dangerous driving.",
            penalty = "₹1,000 to ₹5,000 fine and potential license suspension up to 3 months (Section 184(c)).",
            sourceName = "Motor Vehicles Act Section 184",
            sourceUrl = "https://morth.nic.in",
            iconEmoji = "📱",
            keywords = listOf("mobile phone", "phone", "cellphone", "texting", "calling while driving")
        ),
        TrafficRuleItem(
            id = "licence_documents",
            title = "Mandatory Vehicle Documentation & DL",
            rule = "Drivers must carry or store electronically in DigiLocker/mParivahan: Valid Driving Licence, Registration Certificate (RC), Pollution Under Control (PUC) certificate, and Third-Party Insurance.",
            penalty = "Driving without licence: ₹5,000 fine (Section 181). Without insurance: ₹2,000 to ₹4,000 fine and/or up to 3 months prison (Section 196).",
            sourceName = "Parivahan Sewa / MoRTH",
            sourceUrl = "https://parivahan.gov.in",
            iconEmoji = "📄",
            keywords = listOf("license", "licence", "driving licence", "rc", "insurance", "puc", "digilocker")
        ),
        TrafficRuleItem(
            id = "emergency_vehicle",
            title = "Giving Way to Emergency Vehicles",
            rule = "Every motorist must draw to the side of the road and allow free passage to emergency vehicles including ambulances and fire service vehicles.",
            penalty = "Fine of ₹10,000 and/or imprisonment up to 6 months for obstructing emergency vehicles (Section 194E of the Motor Vehicles Act).",
            sourceName = "Ministry of Road Transport and Highways",
            sourceUrl = "https://morth.nic.in",
            iconEmoji = "🚨",
            keywords = listOf("emergency vehicle", "ambulance passage", "siren", "fire engine", "give way")
        )
    )

    fun findAnswer(rawQuery: String): Pair<TrafficRuleItem?, List<HelplineItem>> {
        val query = rawQuery.lowercase().trim()

        var matchedRule: TrafficRuleItem? = null

        if (query.contains("traffic sign") || query.contains("explain traffic sign") || query.contains("signs") || query.contains("signal")) {
            matchedRule = trafficRules.first { it.id == "traffic_signs" }
        } else if (query.contains("helmet") || query.contains("pillion") || query.contains("bike")) {
            matchedRule = trafficRules.first { it.id == "helmet" }
        } else if (query.contains("accident") || query.contains("road accident") || query.contains("crash") || query.contains("good samaritan")) {
            matchedRule = trafficRules.first { it.id == "road_accident" }
            val relevantHelplines = helplines.filter { it.id in listOf("road_accident", "highway_1033", "ambulance_108", "emergency") }
            return Pair(matchedRule, relevantHelplines)
        } else if (query.contains("seat belt") || query.contains("seatbelt")) {
            matchedRule = trafficRules.first { it.id == "seatbelt" }
        } else if (query.contains("drunk") || query.contains("alcohol") || query.contains("drink")) {
            matchedRule = trafficRules.first { it.id == "drunk_driving" }
        } else if (query.contains("speed") || query.contains("overspeed")) {
            matchedRule = trafficRules.first { it.id == "speed" }
        } else if (query.contains("phone") || query.contains("mobile")) {
            matchedRule = trafficRules.first { it.id == "mobile_phone" }
        } else if (query.contains("license") || query.contains("licence") || query.contains("insurance") || query.contains("puc")) {
            matchedRule = trafficRules.first { it.id == "licence_documents" }
        } else if (query.contains("emergency vehicle") || query.contains("siren")) {
            matchedRule = trafficRules.first { it.id == "emergency_vehicle" }
        } else {
            for (rule in trafficRules) {
                if (rule.keywords.any { query.contains(it) }) {
                    matchedRule = rule
                    break
                }
            }
        }

        if (matchedRule != null) {
            return Pair(matchedRule, emptyList())
        }

        if (query.contains("emergency") || query.contains("help") || query.contains("danger") || query.contains("police")) {
            return Pair(null, helplines.take(3))
        }

        return Pair(null, emptyList())
    }
}

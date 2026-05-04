package riven.core.enums.core

import com.fasterxml.jackson.annotation.JsonProperty

enum class CommunicationType(val value: String) {
    @JsonProperty("meeting") MEETING("meeting"),
    @JsonProperty("call") CALL("call"),
    @JsonProperty("email") EMAIL("email"),
    @JsonProperty("sms") SMS("sms"),
    @JsonProperty("social-media") SOCIAL_MEDIA("social-media"),
}

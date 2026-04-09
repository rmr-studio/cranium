package riven.core.enums.user

import com.fasterxml.jackson.annotation.JsonProperty

enum class AcquisitionChannel {
    @JsonProperty("GOOGLE_ADS") GOOGLE_ADS,
    @JsonProperty("LINKEDIN") LINKEDIN,
    @JsonProperty("TWITTER") TWITTER,
    @JsonProperty("PRODUCT_HUNT") PRODUCT_HUNT,
    @JsonProperty("REFERRAL") REFERRAL,
    @JsonProperty("ORGANIC_SEARCH") ORGANIC_SEARCH,
    @JsonProperty("DIRECT") DIRECT,
    @JsonProperty("CONTENT_MARKETING") CONTENT_MARKETING,
    @JsonProperty("FACEBOOK") FACEBOOK,
    @JsonProperty("INSTAGRAM") INSTAGRAM,
    @JsonProperty("TIKTOK") TIKTOK,
    @JsonProperty("YOUTUBE") YOUTUBE,
    @JsonProperty("PODCAST") PODCAST,
    @JsonProperty("EVENT") EVENT,
    @JsonProperty("OTHER") OTHER,
}

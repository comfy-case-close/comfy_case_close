package com.fnbx.cashclose.dto.response;

import java.math.BigDecimal;

public record DenominationResponse(short denominationId, String currencyCode, BigDecimal faceValue) {}

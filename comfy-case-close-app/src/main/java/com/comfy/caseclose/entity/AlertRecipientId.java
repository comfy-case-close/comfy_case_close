package com.comfy.caseclose.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class AlertRecipientId implements Serializable {

    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "user_id")
    private Long userId;
}

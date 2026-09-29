package com.apollosuny.apolledgebe.standingorder.mapper;

import com.apollosuny.apolledgebe.standingorder.dto.StandingOrderResponse;
import com.apollosuny.apolledgebe.standingorder.entity.StandingOrder;
import org.springframework.stereotype.Component;

@Component
public class StandingOrderMapper {

    public StandingOrderResponse toResponse(StandingOrder order) {
        return new StandingOrderResponse(
                order.getId(),
                order.getName(),
                order.getAmountVnd(),
                order.getDebitAccount().getId(),
                order.getCreditAccount().getId(),
                order.getDayOfMonth().intValue(),
                order.getNextRunOn(),
                order.isAutoPost(),
                order.getPausedAt(),
                order.getCreatedAt()
        );
    }
}

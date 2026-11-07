package com.mtaaconnect.backend.repository

import com.mtaaconnect.backend.model.Booking
import com.mtaaconnect.backend.model.BookingStatus
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.Instant

class BookingRepositoryCustomImpl(
    private val mongoTemplate: MongoTemplate,
) : BookingRepositoryCustom {
    override fun transitionStatus(
        id: String,
        expectedStatus: BookingStatus,
        newStatus: BookingStatus,
    ): Booking? {
        val query = Query.query(
            Criteria.where("_id").`is`(id)
                .and("status").`is`(expectedStatus),
        )
        val update = Update.update("status", newStatus)
            .set("updatedAt", Instant.now())
        return mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            Booking::class.java,
        )
    }
}

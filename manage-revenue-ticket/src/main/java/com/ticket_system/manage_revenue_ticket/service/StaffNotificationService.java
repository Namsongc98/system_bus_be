package com.ticket_system.manage_revenue_ticket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifies ADMIN staff about tickets that need a follow-up call.
 * Stub in task 0.8: only logs. Firebase delivery is task 4.6.
 */
@Service
public class StaffNotificationService {

    private static final Logger log = LoggerFactory.getLogger(StaffNotificationService.class);

    /**
     * Runs only after the ticket is committed, so a rolled-back booking never notifies anyone.
     * Logs ids only — no customer name or phone.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatRebooked(SeatRebookedEvent event) {
        log.info("Seat re-booked after cancellation: tripId={}, seatNumber={}, ticketId={}, customerId={}",
                event.tripId(), event.seatNumber(), event.ticketId(), event.customerId());

        // TODO(4.6 Firebase) — see .claude/docs/plan/screen-feature-plan.md section 4.6:
        //  1. Save a staff_notifications row (type SEAT_REBOOKED, trip_id, seat_number, ticket_id,
        //     customer_id, status NEW) so ADMIN can list it via GET /api/notification.
        //  2. Load the FCM tokens of ADMIN users (device_tokens) and send a push through the
        //     Firebase Admin SDK (credentials from FIREBASE_CREDENTIALS_PATH). Payload carries ids
        //     only; the ADMIN screen loads name + phone of the new customer to call them.
        //  3. Drop tokens Firebase reports as invalid. Sending failures must not affect the booking.
    }
}

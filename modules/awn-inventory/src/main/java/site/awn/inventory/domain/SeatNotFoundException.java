package site.awn.inventory.domain;

/** 平台座位不存在（按座位号查无此座） */
public class SeatNotFoundException extends RuntimeException {

    public SeatNotFoundException(String seatNo) {
        super("seat not found: " + seatNo);
    }
}

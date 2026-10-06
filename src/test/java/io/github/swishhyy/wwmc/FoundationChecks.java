package io.github.swishhyy.wwmc;
import io.github.swishhyy.wwmc.core.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class FoundationChecks {
    @Test void boundsAndReservations() { main(new String[0]); }
    private static int checks;
    private static void check(boolean result,String message) { checks++; if(!result) throw new AssertionError(message); }
    public static void main(String[] args) {
        RoomBounds room=RoomBounds.between(5,8,9,-2,3,4);
        check(room.contains(-2,3,4) && room.contains(5,8,9),"Both selected corners belong to the room");
        check(!room.contains(6,8,9),"Furniture outside the selection is excluded");
        check(room.intersects(new RoomBounds(5,8,9,6,9,10)),"A shared block would give furniture two roles");
        check(!room.intersects(new RoomBounds(6,3,4,8,8,9)),"Adjacent disjoint rooms are allowed");
        check(new RoomBounds(0,0,0,15,15,15).withinLimit(4096),"The maximum room fits");
        check(!new RoomBounds(0,0,0,16,15,15).withinLimit(4096),"Oversize scans are rejected");
        check(!new RoomBounds(Integer.MIN_VALUE,0,0,Integer.MAX_VALUE,1,1).withinLimit(4096),"Coordinate overflow cannot bypass the scan limit");
        check(!StructureRole.HOSPITAL.providesHousing(),"Patient beds must never increase recruitment capacity");
        check(StructureRole.BARRACKS.providesHousing() && StructureRole.HOUSING.providesHousing(),"Camp and home beds are residential");
        check(StructureRole.FARM.providesWork() && !StructureRole.HOSPITAL.providesWork(),"Only implemented economic jobs enter the worker scheduler");
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        ReservationBook<String> claims=new ReservationBook<>();
        check(claims.claim("ore",a,0,200),"First worker reserves an ore block");
        check(!claims.claim("ore",b,10,200),"Second worker cannot harvest the same resource");
        claims.release("ore",b);
        check(!claims.claim("ore",b,20,200),"A different worker cannot release the original reservation");
        check(claims.claim("ore",a,100,200),"Active workers renew their lease");
        check(!claims.claim("ore",b,250,200),"Renewal extends exclusivity");
        check(claims.claim("ore",b,300,200),"Expired claims recover after an unloaded worker");
        claims.release("ore",a);
        check(!claims.claim("ore",a,301,200),"Former owners cannot remove replacement leases");
        claims.release("ore",b);
        check(claims.claim("ore",a,302,200),"Completed work releases the resource");
        System.out.println("Passed "+checks+" foundation checks.");
    }
}

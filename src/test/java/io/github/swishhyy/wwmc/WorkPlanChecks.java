package io.github.swishhyy.wwmc;

import io.github.swishhyy.wwmc.core.*;
import java.util.*;
import org.junit.jupiter.api.Test;

public final class WorkPlanChecks {
    @Test void sharedCrewsAndExcavationGeometry() { main(new String[0]); }
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        WorkforceBook<String> crews=new WorkforceBook<>();
        check(crews.claim("farm",a,0,200,2) && crews.claim("farm",b,0,200,2),"Two workers share a station");
        check(!crews.claim("farm",c,1,200,2),"Crew capacity stays bounded");
        check(crews.claim("farm",a,100,200,2) && crews.count("farm",100)==2,"Renewal never consumes another slot");
        check(crews.claim("farm",c,200,200,2) && crews.count("farm",200)==2,"An unloaded worker's slot expires");
        crews.release("farm",b);
        check(crews.count("farm",200)==2,"An expired worker cannot release another worker's slot");
        crews.release("farm",a);
        check(crews.claim("farm",b,201,200,2),"Leaving a job opens the slot for another worker");
        check(!crews.claim("closed",a,0,200,0),"A zero-capacity station takes no workers");
        crews.prune(1000); check(crews.count("farm",1000)==0,"Shutdown/idle leases recover");

        ReservationBook<String> blocks=new ReservationBook<>();
        blocks.claim("head",a,0,200);
        check(!blocks.claimAll(List.of("foot","head"),b,1,200),"Overlapping tunnel cuts remain exclusive");
        check(blocks.claim("foot",c,1,200),"A failed multi-block claim does not leak its first reservation");
        check(!blocks.available("head",b,1) && blocks.available("head",a,1),"Work searches skip another worker's reserved tree");
        check(blocks.claimAll(List.of("foot","head"),b,201,200),"An entire expired cut can be claimed atomically");

        List<MiningLayout.Cut> tunnel=MiningLayout.tunnel(0,64,0,0,-1,-48,24,4);
        check(tunnel.size()==112*3+4*3*2+24*4*2*2,"Tunnel geometry has bounded stairs, a main corridor and eight branches");
        check(tunnel.get(0).foot().equals(new MiningLayout.Cell(0,63,-2)),"The descent starts beyond the station footprint");
        check(tunnel.get(0).offsetY()==2 && tunnel.get(1).offsetY()==1 && tunnel.get(2).offsetY()==0,"Descending stairs clear headroom before the foot block");
        check(tunnel.get(111*3).foot().y()==-48,"The last stair reaches the configured floor Y");
        check(tunnel.stream().allMatch(cut -> cut.block().y()>=-48),"The mine never digs below its planned floor");
        check(tunnel.stream().map(MiningLayout.Cut::block).distinct().count()==tunnel.size(),"A tunnel plan never awards the same block twice");
        var previous=tunnel.get(3).stand();
        check(previous.equals(tunnel.get(0).foot()),"Each stair is approached from the previously excavated step");
        Set<Integer> branchX=new HashSet<>();
        for(int i=112*3+4*3*2;i<112*3+4*3*2+16;i++) branchX.add(tunnel.get(i).foot().x());
        check(branchX.contains(-1) && branchX.contains(1),"Branch fronts run on both sides of the main tunnel");
        var rotated=MiningLayout.tunnel(10,70,-20,1,0,65,6,2);
        check(rotated.get(0).foot().equals(new MiningLayout.Cell(12,69,-20)),"Station facing rotates the access tunnel");

        RoomBounds quarry=MiningLayout.quarry(-1,-1,1,0,70,-64);
        check(quarry.minX()==0 && quarry.maxX()==15 && quarry.minZ()==-16 && quarry.maxZ()==-1,"Quarry alignment uses floor division for negative coordinates");
        check(!quarry.contains(-1,70,-1),"The quarry operator stays outside the excavated chunk");
        check(MiningLayout.quarryCell(quarry,0).equals(new MiningLayout.Cell(0,70,-16)),"Quarries begin at the top layer");
        check(MiningLayout.quarryCell(quarry,255).equals(new MiningLayout.Cell(15,70,-1)),"All 256 columns fit one layer");
        check(MiningLayout.quarryCell(quarry,256).y()==69,"The next layer follows the previous layer");
        int total=(70+64+1)*256;
        check(MiningLayout.quarryCell(quarry,total-1).equals(new MiningLayout.Cell(15,-64,-1)),"Quarries finish at the requested bottom Y");
        Set<MiningLayout.Cell> all=new HashSet<>();
        for(int i=0;i<total;i++) all.add(MiningLayout.quarryCell(quarry,i));
        check(all.size()==total,"A quarry covers the full chunk column without duplicate cells");
        boolean invalid=false;
        try { MiningLayout.tunnel(0,64,0,1,1,0,24,4); } catch(IllegalArgumentException expected) { invalid=true; }
        check(invalid,"Invalid diagonal layouts cannot start an excavation");
        System.out.println("Passed "+checks+" workforce and excavation checks.");
    }
}

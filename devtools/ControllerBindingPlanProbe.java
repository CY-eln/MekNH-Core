import c_y.meknh_core.api.multiblock.ControllerBindingPlan;
import c_y.meknh_core.api.multiblock.ControllerBindingPlan.Request;
import c_y.meknh_core.api.multiblock.ControllerBindingPlan.Candidate;
import java.util.*;

/** Standalone devtools only: compile alongside ControllerBindingPlan, without Minecraft. */
public final class ControllerBindingPlanProbe {
    static final String P = "meknh_core:primitive_pump", OLD = "meknh_core:gtnh_layout_mtesteamwaterpump_main_133";
    static final List<Candidate> C = List.of(new Candidate(P, OLD));
    static int checks;
    static Request r(String id, boolean none, String block, String... generated) {
        return new Request(id, none, block, List.of(generated));
    }
    static Map<String,String> plan(Request... requests) { return ControllerBindingPlan.plan(List.of(requests), C); }
    static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++; System.out.println("PASS " + label);
    }
    static String fails(String label, Request... requests) {
        try { plan(requests); } catch (IllegalArgumentException e) { checks++; System.out.println("PASS " + label + ": " + e.getMessage()); return e.getMessage(); }
        throw new AssertionError(label);
    }
    public static void main(String[] args) {
        Request legacy = r(OLD, true, null, OLD);
        Request explicit = r("test:new", true, P, "meknh_core:new");
        check(plan(r("meknh_core:layout_mtesteamwaterpump", true, P, "meknh_core:layout_mtesteamwaterpump"))
                .equals(Map.of(P, "meknh_core:layout_mtesteamwaterpump")), "waterpump.json explicit controller binding");
        check(plan(legacy).equals(Map.of(P,OLD)), "legacy default uses parsed gtnh ID");
        check(plan(explicit).equals(Map.of(P,"test:new")), "explicit selection");
        check(plan(legacy,explicit).equals(Map.of(P,"test:new")), "explicit cross-definition overrides fallback without deleting legacy");
        check(plan(legacy,explicit).equals(plan(explicit,legacy)), "successful order independence");
        check(plan(r("test:coke", true,"meknh_core:coke","meknh_core:coke")).isEmpty(), "native self no-op");
        check(plan(r("test:native", false,"meknh_core:lv_native","meknh_core:lv_native")).isEmpty(), "single-level electric native self");
        check(plan(r("test:plain",true,null,"meknh_core:plain")).isEmpty(), "ordinary default generates controller");
        fails("cross-definition native forbidden",r("test:new",true,"meknh_core:coke","meknh_core:new"));
        fails("ordinary block forbidden",r("test:new",true,"minecraft:stone","meknh_core:new"));
        fails("unknown block forbidden",r("test:new",true,"test:missing","meknh_core:new"));
        fails("external non-none forbidden",r("test:new",false,P,"meknh_core:lv_new"));
        fails("duplicate parsed ID",legacy,legacy);
        fails("invalid ResourceLocation",r(OLD,true,"Bad ID",OLD));
        fails("multi-level native forbidden",r("test:multi",false,"meknh_core:lv_multi","meknh_core:lv_multi","meknh_core:mv_multi"));
        fails("invalid explicit cannot fallback",r(OLD,true,"test:missing",OLD));
        Request second = r("test:second",true,P,"meknh_core:second");
        String a = fails("same-block conflict rejects batch",legacy,explicit,second);
        String b = fails("reverse conflict rejects batch",second,explicit,legacy);
        check(a.equals(b), "failure order independence");
        check(plan(r("meknh_core:primitive_pump",true,P,P)).equals(Map.of(P,"meknh_core:primitive_pump")), "external candidate wins over native self declaration");
        fails("implicit generator cannot claim external candidate",legacy,r("test:primitive_pump",true,null,P));
        fails("cross-namespace generated collision",r("a:same",true,null,"meknh_core:same"),r("b:same",true,null,"meknh_core:same"));
        fails("generated collision different definitions",r("a:x",true,null,"meknh_core:shared"),r("b:y",true,null,"meknh_core:shared"));
        try {
            ControllerBindingPlan.plan(List.of(r("meknh_core:pump_deck",true,"meknh_core:pump_deck","meknh_core:pump_deck")),C,Set.of("meknh_core:pump_deck"));
            throw new AssertionError("native self cannot replace ordinary casing");
        } catch (IllegalArgumentException e) { checks++; System.out.println("PASS native self cannot replace ordinary casing: " + e.getMessage()); }
        check(ControllerBindingPlan.plan(List.of(r("meknh_core:primitive_pump",true,P,P)),C,Set.of(P)).equals(Map.of(P,"meknh_core:primitive_pump")), "reserved external ID still reusable by candidate");
        check(plan(legacy, r("test:coke",true,"meknh_core:coke","meknh_core:coke")).equals(Map.of(P,OLD)), "native no-op does not consume external ownership");
        System.out.println("ControllerBindingPlanProbe: " + checks + " checks passed");
    }
}

import c_y.meknh_core.client.structure.StructureCandidateGrid;

public class StructureCandidateGridProbe {
    static void check(boolean value) { if (!value) throw new AssertionError("candidate grid geometry"); }
    public static void main(String[] args) {
        int checks = 0;
        for (int available = 22; available <= 320; available++) {
            int columns = StructureCandidateGrid.columns(available);
            int width = StructureCandidateGrid.width(columns);
            check(width <= available && available - width < 18);
            for (int count = 1; count <= 512; count++) {
                int last = count - 1;
                int x = StructureCandidateGrid.x(last, columns);
                int y = StructureCandidateGrid.y(last, columns);
                check(x >= 3 && x + 16 <= width - 3);
                check(y + 16 == StructureCandidateGrid.height(count, columns) - 3);
                if (count > columns) check(y - StructureCandidateGrid.y(last - columns, columns) == 18);
                if (last % columns > 0) check(x - StructureCandidateGrid.x(last - 1, columns) == 18);
                checks++;
            }
        }
        System.out.println("PASS " + checks + " grid layouts: 2px edge clearance and horizontal/vertical gaps");
    }
}

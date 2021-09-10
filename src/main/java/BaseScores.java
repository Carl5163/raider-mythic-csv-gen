
public class BaseScores {
	
	public static final int BASESCORES[] = {
			  0,
			  0, 40, 45, 55, 60, 65, 75, 80, 85,100,
			105,110,115,120,125,130,135,140,145,150,
			155,160,165,170,175,180,185,190,195,200
	};
	
	public static int get(int keyLevel) {
		return BASESCORES[keyLevel];
	}
}

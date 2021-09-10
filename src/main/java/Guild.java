import java.util.List;

public class Guild {
	List<Member> members;
	public class Member {
		Character character;
		public class Character {
			String name;
			Realm realm;
			public class Realm {
				String slug;
			}
		}
		String getRealm() {
			return character.realm.slug;
		}
		String getName() {
			return character.name;
		}
		@Override 
		public String toString() {
			return character.name + ", " + character.realm.slug;
		}
	}
	public List<Member> getMembers() {
		return members;
	}
}

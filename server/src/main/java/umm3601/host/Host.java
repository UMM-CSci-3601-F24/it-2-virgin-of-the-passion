package umm3601.host;

import org.mongojack.Id;
import org.mongojack.ObjectId;

@SuppressWarnings({"VisibilityModifier"})
public class Host {

    @ObjectId @Id
    @SuppressWarnings({"MemberName"})
    public String _id;
    public String name;

    // We might have something in here needed to mark what searches and grids blong to a group
    // What the people of last semester had:
    // public String userName;
    // public String email;

}

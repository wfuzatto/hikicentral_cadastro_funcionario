package br.com.acquavale.bridge;
import java.util.List;
public interface ArtemisGateway {
 record Group(String id,String name){}
 String rootOrganization();
 List<Group> groups();
 String createPerson(String name,int gender,byte[] jpeg,String org,String personCode);
 void assign(String personId,String groupId);
 void reapply();
 boolean allApplied();
}

/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli;

import org.bedework.access.WhoDefs;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwUser;
import org.bedework.calfacade.svc.BwAdminGroup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * User: mike
 * Date: 6/13/15
 * Time: 11:08 PM
 */
public class JsonMapper extends ObjectMapper {
  public JsonMapper() {
    setSerializationInclusion(JsonInclude.Include.NON_NULL);
    // configure(JsonFactory.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

//    final SimpleModule sm = new SimpleModule();
//    sm.addAbstractTypeMapping(Principal.class, UserPrincipal.class);
//    sm.addAbstractTypeMapping(AffiliationInfo.class, AffiliationInfoImpl.class);

    final SimpleModule module =
            new SimpleModule("Bw(De)serializerModule",
                             new Version(1, 0, 0, null));
    final PrincipalDeserializer deserializer =
            new PrincipalDeserializer();
    deserializer.registerEntity(WhoDefs.whoTypeUser,
                                BwUser.class);
    deserializer.registerEntity(WhoDefs.whoTypeGroup,
                                BwGroup.class);

    module.addDeserializer(BwPrincipal.class, deserializer);

    registerModule(module);
//    registerModule(sm);
  }

  private class PrincipalDeserializer extends StdDeserializer<BwPrincipal> {
    private final Map<Integer, Class<? extends BwPrincipal>> registry =
            new HashMap<>();

    PrincipalDeserializer() {
      super(BwPrincipal.class);
    }

    void registerEntity(final int kind,
                        final Class<? extends BwPrincipal> cl) {
      registry.put(kind, cl);
    }

    @Override
    public BwPrincipal deserialize(final JsonParser jp,
                                  final DeserializationContext ctxt)
            throws IOException {
      final ObjectMapper mapper = (ObjectMapper) jp.getCodec();
      final ObjectNode root = mapper.readTree(jp);

      final JsonNode node = root.get("kind");

      if (node == null) {
        return null;
      }

      final Number kind = node.numberValue();

      Class<? extends BwPrincipal> cl = registry.get(kind.intValue());

      if (cl == null) {
        return null;
      }
      
      System.out.println("Got class " + cl + " kind " + kind.intValue());
      if (kind.intValue() == WhoDefs.whoTypeGroup) {
        final JsonNode gonode = root.get("groupOwnerHref");

        if (gonode != null) {
          System.out.println("Class now " + cl);
          cl = BwAdminGroup.class;
        }
      }

      return mapper.treeToValue(root, cl);
    }
  }
}

package io.yak.ops.api.dto.resources.visitor;

import io.yak.ops.api.dto.resources.ResourceComponent;
import io.yak.ops.plugin.storage.api.StorageEntity;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResourceTreeVisitorTest {

    @Test
    public void visit() {
        List<StorageEntity> resourceList = new ArrayList<>();

        StorageEntity resource1 = new StorageEntity();
        resource1.setFullName("/default/a");
        resource1.setPfullName("/default");

        StorageEntity resource2 = new StorageEntity();
        resource1.setFullName("/default/a/a1.txt");
        resource1.setPfullName("/default/a");

        resourceList.add(resource1);
        resourceList.add(resource2);

        ResourceTreeVisitor resourceTreeVisitor = new ResourceTreeVisitor(resourceList);
        ResourceComponent resourceComponent = resourceTreeVisitor.visit("/default");
        Assertions.assertNotNull(resourceComponent.getChildren());
    }

    @Test
    public void rootNode() {
        List<StorageEntity> resourceList = new ArrayList<>();

        StorageEntity resource1 = new StorageEntity();
        resource1.setFullName("/default/a");
        resource1.setPfullName("/default");

        StorageEntity resource2 = new StorageEntity();
        resource1.setFullName("/default/a/a1.txt");
        resource1.setPfullName("/default/a");

        resourceList.add(resource1);
        resourceList.add(resource2);

        ResourceTreeVisitor resourceTreeVisitor = new ResourceTreeVisitor(resourceList);
        Assertions.assertTrue(resourceTreeVisitor.rootNode(resource1, "/default"));
        Assertions.assertFalse(resourceTreeVisitor.rootNode(resource2, "/default"));
    }

}

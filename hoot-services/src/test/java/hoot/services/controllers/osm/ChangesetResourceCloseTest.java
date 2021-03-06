/*
 * This file is part of Hootenanny.
 *
 * Hootenanny is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * --------------------------------------------------------------------
 *
 * The following copyright notices are generated automatically. If you
 * have a new notice to add, please use the format:
 * " * @copyright Copyright ..."
 * This will properly maintain the copyright information. DigitalGlobe
 * copyrights will be updated automatically.
 *
 * @copyright Copyright (C) 2013, 2014, 2015 DigitalGlobe (http://www.digitalglobe.com/)
 */
package hoot.services.controllers.osm;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.xpath.XPathAPI;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import hoot.services.HootProperties;
import hoot.services.UnitTest;
import hoot.services.db.DbUtils;

import hoot.services.db.postgres.PostgresUtils;
import hoot.services.db2.CurrentNodes;
import hoot.services.db2.CurrentRelationMembers;
import hoot.services.db2.CurrentRelations;
import hoot.services.db2.CurrentWayNodes;
import hoot.services.db2.CurrentWays;
import hoot.services.db2.QChangesets;
import hoot.services.db2.QCurrentNodes;
import hoot.services.db2.QCurrentRelationMembers;
import hoot.services.db2.QCurrentRelations;
import hoot.services.db2.QCurrentWayNodes;
import hoot.services.db2.QCurrentWays;
import hoot.services.geo.BoundingBox;
import hoot.services.geo.GeoUtils;
import hoot.services.geo.QuadTileCalculator;
import hoot.services.osm.OsmResourceTestAbstract;
import hoot.services.osm.OsmTestUtils;
import hoot.services.utils.XmlDocumentBuilder;

import com.mysema.query.sql.SQLQuery;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;

/*
 * @todo Most of these tests could be converted to integration tests and after a refactoring,
 * could be replace with unit tests that test only the internal classes being used by this
 * Jersey resource.
 */
public class ChangesetResourceCloseTest extends OsmResourceTestAbstract
{
  private static final Logger log = LoggerFactory.getLogger(ChangesetResourceCloseTest.class);
  private QCurrentNodes currentNodesTbl = QCurrentNodes.currentNodes;
  private QCurrentWays currentWaysTbl = QCurrentWays.currentWays;
  private QCurrentWayNodes currentWayNodesTbl = QCurrentWayNodes.currentWayNodes;
  private QCurrentRelations  currentRelationsTbl = QCurrentRelations.currentRelations;
  private QCurrentRelationMembers currentRelationMembersTbl = QCurrentRelationMembers.currentRelationMembers;

  public ChangesetResourceCloseTest() throws NumberFormatException, IOException
  {
    super("hoot.services.controllers.osm");
  }

  @Test
  @Category(UnitTest.class)
  public void testClose() throws Exception
  {
    try
    {
      final BoundingBox originalBounds = OsmTestUtils.createStartingTestBounds();
      final long changesetId = OsmTestUtils.createTestChangeset(originalBounds);

      //close the changeset via the service
      try
      {
        ClientResponse response =
          resource()
            .path("api/0.6/changeset/" + changesetId + "/close")
            .queryParam("mapId", String.valueOf(mapId))
            .put(ClientResponse.class, "");
        Assert.assertEquals(200, response.getStatus());
      }
      catch (UniformInterfaceException e)
      {
        ClientResponse r = e.getResponse();
        Assert.fail("Unexpected response " + r.getStatus() + " " + r.getEntity(String.class));
      }

      OsmTestUtils.verifyTestChangesetClosed(changesetId);
    }
    catch (Exception e)
    {
      log.error(e.getMessage());
      throw e;
    }
  }

  @Test(expected=Exception.class)
  @Category(UnitTest.class)
  public void testCloseClosedChangeset() throws Exception
  {
    final BoundingBox originalBounds = OsmTestUtils.createStartingTestBounds();
    final long changesetId = OsmTestUtils.createTestChangeset(originalBounds);

    //close the changeset
    OsmTestUtils.closeChangeset(mapId, changesetId);
    QChangesets changesets = QChangesets.changesets;
    hoot.services.db2.Changesets changeset =
    new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(changesets)
    .where(changesets.id.eq(changesetId)).singleResult(changesets);
    final Timestamp now = new Timestamp(Calendar.getInstance().getTimeInMillis());
    Thread.sleep(1000);
    Assert.assertTrue(changeset.getClosedAt().before(now));

    ClientResponse response = null;
    try
    {
      //Try to close an already closed changeset.  A failure should occur and no data in the
      //system should be modified.
      response =
        resource()
         .path("api/0.6/changeset/" + changesetId + "/close")
         .put(ClientResponse.class, "");
    }
    catch (Exception e)
    {
      Assert.assertEquals(Status.CONFLICT, Status.fromStatusCode(response.getStatus()));
      Assert.assertTrue(
        response.getEntity(String.class).contains(
          "The changeset with ID: " + changesetId + " was closed at"));

      OsmTestUtils.verifyTestChangesetUnmodified(
        changesetId, originalBounds);
      OsmTestUtils.verifyTestChangesetClosed(changesetId);

      throw e;
    }
  }

  @Test(expected=Exception.class)
  @Category(UnitTest.class)
  public void testCloseNonExistingChangeset() throws Exception
  {
    //Try to close a changeset that doesn't exist.  A failure should occur and no data in
    //system should be modified.
    ClientResponse response = null;
    try
    {
      //close a changeset that doesn't exist
      response =
         resource()
          .path("api/0.6/changeset/1/close")
          .put(ClientResponse.class, "");
    }
    catch (Exception e)
    {
      Assert.assertEquals(404, response.getStatus());
      Assert.assertTrue(response.getEntity(String.class).contains(
        "Changeset to be updated does not exist"));

      throw e;
    }
  }

  @Test(expected=UniformInterfaceException.class)
  @Category(UnitTest.class)
  public void testChangesetMaxElementsExceededUploadedToEmptyChangeset() throws Exception
  {
    Properties hootProps = HootProperties.getInstance();
    //lower the max allowed elements per changeset from the default
    final int maximumChangesetElements = 2;
    hootProps.setProperty("maximumChangesetElements", String.valueOf(maximumChangesetElements));
    HootProperties.setProperties(hootProps);
    Assert.assertEquals(
      maximumChangesetElements,
      Integer.parseInt(HootProperties.getInstance().getProperty("maximumChangesetElements")));

    try
    {
      final BoundingBox originalBounds = OsmTestUtils.createStartingTestBounds();
      final long changesetId = OsmTestUtils.createTestChangeset(originalBounds, 0);

      //Now create a new changeset with a number of elements larger than the max allowed.  A failure
      //should occur and no data in the system should be modified.
      try
      {
        resource()
          .path("api/0.6/changeset/" + changesetId + "/upload")
          .queryParam("mapId", "" + mapId)
          .type(MediaType.TEXT_XML)
          .accept(MediaType.TEXT_XML)
          .post(
            Document.class,
            "<osmChange version=\"0.3\" generator=\"iD\">" +
              "<create>" +
                "<node id=\"-1\" lon=\"" + originalBounds.getMinLon() + "\" lat=\"" +
                  originalBounds.getMinLat() + "\" version=\"0\" changeset=\"" + changesetId + "\">" +
                  "<tag k=\"key 1\" v=\"val 1\"/>" +
                  "<tag k=\"key 2\" v=\"val 2\"/>" +
                "</node>" +
                "<node id=\"-2\" lon=\"" + originalBounds.getMaxLon() + "\" lat=\"" +
                  originalBounds.getMaxLat() + "\" version=\"0\" changeset=\"" + changesetId + "\">" +
                "</node>" +
                "<node id=\"-3\" lon=\"" + originalBounds.getMinLon() + "\" lat=\"" +
                  originalBounds.getMinLat() + "\" version=\"0\" changeset=\"" + changesetId + "\">" +
                "</node>" +
              "</create>" +
              "<modify/>" +
              "<delete if-unused=\"true\"/>" +
            "</osmChange>");
      }
      catch (UniformInterfaceException e)
      {
        ClientResponse r = e.getResponse();
        Assert.assertEquals(Status.CONFLICT, Status.fromStatusCode(r.getStatus()));
        Assert.assertTrue(
          r.getEntity(String.class).contains("Changeset maximum element threshold exceeded"));

        try
        {
        	QChangesets changesets = QChangesets.changesets;
          hoot.services.db2.Changesets changeset =
          new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(changesets)
          .where(changesets.id.eq(changesetId)).singleResult(changesets);

          Assert.assertNotNull(changeset);
          final Timestamp now = new Timestamp(Calendar.getInstance().getTimeInMillis());
          Thread.sleep(1000);
          Assert.assertTrue(changeset.getCreatedAt().before(now));
          Assert.assertTrue(changeset.getClosedAt().after(changeset.getCreatedAt()));
          Assert.assertEquals(new Integer(0), changeset.getNumChanges());
          Assert.assertEquals(new Long(userId), changeset.getUserId());

          //make sure the changeset bounds wasn't updated
          hoot.services.models.osm.Changeset hootChangeset =
            new hoot.services.models.osm.Changeset(mapId,changesetId, conn);
          BoundingBox changesetBounds = hootChangeset.getBounds();
          BoundingBox defaultBounds = new BoundingBox();
          //a change the size of the expansion factor is made automatically, so the changeset's
          //bounds should be no larger than that
          defaultBounds.expand(
            originalBounds,
            Double.parseDouble(HootProperties.getDefault("changesetBoundsExpansionFactorDeegrees")));
          Assert.assertTrue(changesetBounds.equals(defaultBounds));
        }
        catch (Exception e2)
        {
          Assert.fail("Error checking changeset: " + e2.getMessage());
        }

        throw e;
      }
    }
    catch (Exception e)
    {
      log.error(e.getMessage());
      throw e;
    }
    finally
    {
      //set this back to default now that this test is over
      HootProperties.getInstance().setProperty(
        "maximumChangesetElements", HootProperties.getDefault("maximumChangesetElements"));
      Assert.assertEquals(
        Integer.parseInt(HootProperties.getDefault("maximumChangesetElements")),
        Integer.parseInt(HootProperties.getInstance().getProperty("maximumChangesetElements")));
    }
  }

  @Test
  @Category(UnitTest.class)
  public void testChangesetAutoCloseWhenMaxElementsUploadedToEmptyChangeset() throws Exception
  {
    Properties hootProps = HootProperties.getInstance();
    //lower the max allowed elements per changeset from the default to the number of elements
    //we're uploading
    final int maximumChangesetElements = 12;
    hootProps.setProperty("maximumChangesetElements", String.valueOf(maximumChangesetElements));
    HootProperties.setProperties(hootProps);
    Assert.assertEquals(
      maximumChangesetElements,
      Integer.parseInt(HootProperties.getInstance().getProperty("maximumChangesetElements")));

    try
    {
      final BoundingBox originalBounds = OsmTestUtils.createStartingTestBounds();
      final long changesetId = OsmTestUtils.createTestChangeset(originalBounds, 0);

      //Now create a new changeset with a number of elements equal to the max allowed.  The elements
      //should be written and the changeset closed.
      Document responseData = null;
      try
      {
        responseData =
          resource()
            .path("api/0.6/changeset/" + changesetId + "/upload")
            .queryParam("mapId", "" + mapId)
            .type(MediaType.TEXT_XML)
            .accept(MediaType.TEXT_XML)
            .post(
              Document.class,
              "<osmChange version=\"0.3\" generator=\"iD\">" +
                "<create>" +
                  "<node id=\"-1\" lon=\"" + originalBounds.getMinLon() + "\" lat=\"" +
                    originalBounds.getMinLat() + "\" version=\"0\" changeset=\"" + changesetId + "\">" +
                    "<tag k=\"key 1\" v=\"val 1\"/>" +
                    "<tag k=\"key 2\" v=\"val 2\"/>" +
                  "</node>" +
                  "<node id=\"-2\" lon=\"" + originalBounds.getMaxLon() + "\" lat=\"" +
                    originalBounds.getMaxLat() + "\" version=\"0\" changeset=\"" + changesetId + "\">" +
                  "</node>" +
                  "<node id=\"-3\" lon=\"" + originalBounds.getMinLon() + "\" lat=\"" +
                    originalBounds.getMinLat() + "\" version=\"0\" changeset=\"" + changesetId + "\">" +
                  "</node>" +
                  "<node id=\"-4\" lon=\"" + originalBounds.getMinLon() + "\" lat=\"" +
                    originalBounds.getMinLat() + "\" version=\"0\" changeset=\"" + changesetId + "\">" +
                    "<tag k=\"key 3\" v=\"val 3\"/>" +
                  "</node>" +
                  "<node id=\"-5\" lon=\"" + originalBounds.getMinLon() + "\" lat=\"" +
                    originalBounds.getMinLat() + "\" version=\"0\" changeset=\"" + changesetId + "\">" +
                    "<tag k=\"key 4\" v=\"val 4\"/>" +
                  "</node>" +
                  "<way id=\"-6\" version=\"0\" changeset=\"" + changesetId + "\" >" +
                    "<nd ref=\"-1\"></nd>" +
                    "<nd ref=\"-2\"></nd>" +
                    "<nd ref=\"-5\"></nd>" +
                    "<tag k=\"key 1\" v=\"val 1\"/>" +
                    "<tag k=\"key 2\" v=\"val 2\"/>" +
                  "</way>" +
                  "<way id=\"-7\" version=\"0\" changeset=\"" + changesetId + "\" >" +
                    "<nd ref=\"-3\"></nd>" +
                    "<nd ref=\"-2\"></nd>" +
                  "</way>" +
                  "<way id=\"-8\" version=\"0\" changeset=\"" + changesetId + "\" >" +
                    "<nd ref=\"-1\"></nd>" +
                    "<nd ref=\"-2\"></nd>" +
                    "<tag k=\"key 3\" v=\"val 3\"/>" +
                  "</way>" +
                  "<relation id=\"-9\" version=\"0\" changeset=\"" + changesetId + "\" >" +
                    "<member type=\"node\" role=\"role1\" ref=\"-1\"></member>" +
                    "<member type=\"way\" role=\"role3\" ref=\"-7\"></member>" +
                    "<member type=\"way\" role=\"role2\" ref=\"-6\"></member>" +
                    "<member type=\"node\" ref=\"-3\"></member>" +
                    "<tag k=\"key 1\" v=\"val 1\"/>" +
                  "</relation>" +
                  "<relation id=\"-10\" version=\"0\" changeset=\"" + changesetId + "\" >" +
                    "<member type=\"node\" role=\"role1\" ref=\"-5\"></member>" +
                    "<member type=\"relation\" role=\"role1\" ref=\"-9\"></member>" +
                    "<tag k=\"key 2\" v=\"val 2\"/>" +
                    "<tag k=\"key 3\" v=\"val 3\"/>" +
                  "</relation>" +
                  "<relation id=\"-11\" version=\"0\" changeset=\"" + changesetId + "\" >" +
                    "<member type=\"way\" role=\"\" ref=\"-7\"></member>" +
                    "<tag k=\"key 4\" v=\"val 4\"/>" +
                  "</relation>" +
                  "<relation id=\"-12\" version=\"0\" changeset=\"" + changesetId + "\" >" +
                    "<member type=\"node\" role=\"role1\" ref=\"-3\"></member>" +
                  "</relation>" +
                "</create>" +
                "<modify/>" +
                "<delete if-unused=\"true\"/>" +
              "</osmChange>");
      }
      catch (UniformInterfaceException e)
      {
        ClientResponse r = e.getResponse();
        Assert.fail("Unexpected response " + r.getStatus() + " " + r.getEntity(String.class));
      }
      Assert.assertNotNull(responseData);

      XPath xpath = XmlDocumentBuilder.createXPath();
      Set<Long> nodeIds = new LinkedHashSet<Long>();
      Set<Long> wayIds = new LinkedHashSet<Long>();
      Set<Long> relationIds = new LinkedHashSet<Long>();
      try
      {
        NodeList returnedNodes = XPathAPI.selectNodeList(responseData, "//osm/diffResult/node");
        Assert.assertEquals(5, returnedNodes.getLength());

        long oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[1]/@old_id", responseData));
        Assert.assertEquals(-1, oldElementId);
        long newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[1]/@new_id", responseData));
        Assert.assertNotSame(-1, newElementId);
        nodeIds.add(newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/node[1]/@new_version", responseData)));

        oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[2]/@old_id", responseData));
        Assert.assertEquals(-2, oldElementId);
        newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[2]/@new_id", responseData));
        Assert.assertNotSame(-2, newElementId);
        nodeIds.add(newElementId);
        Assert.assertEquals(
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[1]/@new_id", responseData)) + 1,
          newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/node[2]/@new_version", responseData)));

        oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[3]/@old_id", responseData));
        Assert.assertEquals(-3, oldElementId);
        newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[3]/@new_id", responseData));
        Assert.assertNotSame(-3, newElementId);
        nodeIds.add(newElementId);
        Assert.assertEquals(
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[2]/@new_id", responseData)) + 1,
          newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/node[3]/@new_version", responseData)));

        oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[4]/@old_id", responseData));
        Assert.assertEquals(-4, oldElementId);
        newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[4]/@new_id", responseData));
        Assert.assertNotSame(-4, newElementId);
        nodeIds.add(newElementId);
        Assert.assertEquals(
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[3]/@new_id", responseData)) + 1,
          newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/node[4]/@new_version", responseData)));

        oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[5]/@old_id", responseData));
        Assert.assertEquals(-5, oldElementId);
        newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[5]/@new_id", responseData));
        Assert.assertNotSame(-5, newElementId);
        nodeIds.add(newElementId);
        Assert.assertEquals(
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[4]/@new_id", responseData)) + 1,
          newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/node[5]/@new_version", responseData)));

        NodeList returnedWays = XPathAPI.selectNodeList(responseData, "//osm/diffResult/way");
        Assert.assertEquals(3, returnedWays.getLength());

        oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[1]/@old_id", responseData));
        Assert.assertEquals(-6, oldElementId);
        newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[1]/@new_id", responseData));
        Assert.assertNotSame(-6, newElementId);
        wayIds.add(newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/way[1]/@new_version", responseData)));

        oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[2]/@old_id", responseData));
        Assert.assertEquals(-7, oldElementId);
        newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[2]/@new_id", responseData));
        Assert.assertNotSame(-7, newElementId);
        wayIds.add(newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/way[2]/@new_version", responseData)));

        oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[3]/@old_id", responseData));
        Assert.assertEquals(-8, oldElementId);
        newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[3]/@new_id", responseData));
        Assert.assertNotSame(-8, newElementId);
        wayIds.add(newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/way[3]/@new_version", responseData)));

        NodeList returnedRelations =
          XPathAPI.selectNodeList(responseData, "//osm/diffResult/relation");
        Assert.assertEquals(4, returnedRelations.getLength());

        oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[1]/@old_id", responseData));
        Assert.assertEquals(-9, oldElementId);
        newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[1]/@new_id", responseData));
        Assert.assertNotSame(-9, newElementId);
        relationIds.add(newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/relation[1]/@new_version", responseData)));

        oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[2]/@old_id", responseData));
        Assert.assertEquals(-10, oldElementId);
        newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[2]/@new_id", responseData));
        Assert.assertNotSame(-10, newElementId);
        relationIds.add(newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/relation[2]/@new_version", responseData)));

        oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[3]/@old_id", responseData));
        Assert.assertEquals(-11, oldElementId);
        newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[3]/@new_id", responseData));
        Assert.assertNotSame(-11, newElementId);
        relationIds.add(newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/relation[3]/@new_version", responseData)));

        oldElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[4]/@old_id", responseData));
        Assert.assertEquals(-12, oldElementId);
        newElementId =
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[4]/@new_id", responseData));
        Assert.assertNotSame(-12, newElementId);
        relationIds.add(newElementId);
        Assert.assertEquals(
          1, Long.parseLong(xpath.evaluate("//osm/diffResult/relation[4]/@new_version", responseData)));
      }
      catch (XPathExpressionException e)
      {
        Assert.fail("Error parsing response document: " + e.getMessage());
      }

      //changes have actually occurred with the upload of the changeset...what's actually being
      //done here is to compare the state of the default test data set with the dataset we uploaded
      //here, and they should match each other
      OsmTestUtils.verifyTestDataUnmodified(
        originalBounds, changesetId, nodeIds, wayIds, relationIds);
      OsmTestUtils.verifyTestChangesetClosed(changesetId);
    }
    catch (Exception e)
    {
      log.error(e.getMessage());
      throw e;
    }
    finally
    {
      //set this back to default now that this test is over
      HootProperties.getInstance().setProperty(
        "maximumChangesetElements", HootProperties.getDefault("maximumChangesetElements"));
      Assert.assertEquals(
        Integer.parseInt(HootProperties.getDefault("maximumChangesetElements")),
        Integer.parseInt(HootProperties.getInstance().getProperty("maximumChangesetElements")));
    }
  }

  @Test(expected=UniformInterfaceException.class)
  @Category(UnitTest.class)
  public void testChangesetMaxElementsExceededUploadedToExistingChangeset() throws Exception
  {
    Properties hootProps = HootProperties.getInstance();
    //lower the max allowed elements per changeset from the default
    final int maximumChangesetElements = 16;
    hootProps.setProperty("maximumChangesetElements", String.valueOf(maximumChangesetElements));
    HootProperties.setProperties(hootProps);
    Assert.assertEquals(
      maximumChangesetElements,
      Integer.parseInt(HootProperties.getInstance().getProperty("maximumChangesetElements")));

    try
    {
      final BoundingBox originalBounds = OsmTestUtils.createStartingTestBounds();
      final long changesetId = OsmTestUtils.createTestChangeset(originalBounds);
      final Set<Long> nodeIds =
        OsmTestUtils.createTestNodes(changesetId, originalBounds);
      final Long[] nodeIdsArr = nodeIds.toArray(new Long[]{});
      final Set<Long> wayIds =
        OsmTestUtils.createTestWays(changesetId, nodeIds);
      final Long[] wayIdsArr = wayIds.toArray(new Long[]{});
      final Set<Long> relationIds =
        OsmTestUtils.createTestRelations(changesetId, nodeIds, wayIds);
      final Long[] relationIdsArr = relationIds.toArray(new Long[]{});

      final BoundingBox updatedBounds =
        OsmTestUtils.createAfterModifiedTestChangesetBounds();
      //Now update an existing changeset with a number of elements larger than the max allowed.  A
      //failure should occur and no data in the system should be modified.
      try
      {
        resource()
          .path("api/0.6/changeset/" + changesetId + "/upload")
          .queryParam("mapId", ""+mapId)
          .type(MediaType.TEXT_XML)
          .accept(MediaType.TEXT_XML)
          .post(
            Document.class,
            "<osmChange version=\"0.3\" generator=\"iD\">" +
              "<create/>" +
              "<modify>" +
                "<node id=\"" + nodeIdsArr[0] + "\" lon=\"" + updatedBounds.getMinLon() +
                  "\" lat=\"" + updatedBounds.getMinLat() + "\" version=\"1\" changeset=\"" +
                  changesetId + "\">" +
                "</node>" +
                "<node id=\"" + nodeIdsArr[1] + "\" lon=\"" + originalBounds.getMaxLon() +
                  "\" lat=\"" + updatedBounds.getMinLat() + "\" version=\"1\" changeset=\"" +
                  changesetId + "\">" +
                "</node>" +
                "<way id=\"" + wayIdsArr[0] + "\" version=\"1\" changeset=\"" + changesetId + "\" >" +
                  "<nd ref=\"" + nodeIdsArr[0] + "\"></nd>" +
                  "<nd ref=\"" + nodeIdsArr[1] + "\"></nd>" +
                "</way>" +
                "<way id=\"" + wayIdsArr[1] + "\" version=\"1\" changeset=\"" + changesetId + "\" >" +
                  "<nd ref=\"" + nodeIdsArr[0] + "\"></nd>" +
                  "<nd ref=\"" + nodeIdsArr[1] + "\"></nd>" +
                "</way>" +
                "<relation id=\"" + relationIdsArr[0] + "\" version=\"1\" changeset=\"" + changesetId + "\" >" +
                  "<member type=\"way\" role=\"role4\" ref=\"" + wayIdsArr[1] + "\"></member>" +
                  "<member type=\"way\" role=\"role2\" ref=\"" + wayIdsArr[0] + "\"></member>" +
                  "<member type=\"node\" ref=\"" + nodeIdsArr[2] + "\"></member>" +
                "</relation>" +
                "<relation id=\"" + relationIdsArr[1] + "\" version=\"1\" changeset=\"" + changesetId + "\" >" +
                  "<member type=\"relation\" role=\"role1\" ref=\"" + relationIdsArr[0] + "\"></member>" +
                  "<member type=\"node\" ref=\"" + nodeIdsArr[4] + "\"></member>" +
                "</relation>" +
              "</modify>" +
              "<delete if-unused=\"true\"/>" +
            "</osmChange>");
      }
      catch (UniformInterfaceException e)
      {
        ClientResponse r = e.getResponse();
        Assert.assertEquals(Status.CONFLICT, Status.fromStatusCode(r.getStatus()));
        Assert.assertTrue(
          r.getEntity(String.class).contains("Changeset maximum element threshold exceeded"));

        OsmTestUtils.verifyTestDataUnmodified(
          originalBounds, changesetId, nodeIds, wayIds, relationIds);

        throw e;
      }
    }
    catch (Exception e)
    {
      log.error(e.getMessage());
      throw e;
    }
    finally
    {
      //set this back to default now that this test is over
      HootProperties.getInstance().setProperty(
        "maximumChangesetElements", HootProperties.getDefault("maximumChangesetElements"));
      Assert.assertEquals(
        Integer.parseInt(HootProperties.getDefault("maximumChangesetElements")),
        Integer.parseInt(HootProperties.getInstance().getProperty("maximumChangesetElements")));
    }
  }

  @Test
  @Category(UnitTest.class)
  public void testChangesetAutoCloseWhenMaxElementsUploadedToExistingChangeset() throws Exception
  {
    Properties hootProps = HootProperties.getInstance();
    //lower the max allowed elements per changeset from the default to the sum of number of
    //elements existing currently in changeset + what we're adding
    final int maximumChangesetElements = 18;
    hootProps.setProperty("maximumChangesetElements", String.valueOf(maximumChangesetElements));
    HootProperties.setProperties(hootProps);
    Assert.assertEquals(
      maximumChangesetElements,
      Integer.parseInt(HootProperties.getInstance().getProperty("maximumChangesetElements")));

    try
    {
      final BoundingBox originalBounds = OsmTestUtils.createStartingTestBounds();
      final long changesetId = OsmTestUtils.createTestChangeset(originalBounds);
      final Set<Long> nodeIds =
        OsmTestUtils.createTestNodes(changesetId, originalBounds);
      final Long[] nodeIdsArr = nodeIds.toArray(new Long[]{});
      final Set<Long> wayIds =
        OsmTestUtils.createTestWays(changesetId, nodeIds);
      final Long[] wayIdsArr = wayIds.toArray(new Long[]{});
      final Set<Long> relationIds =
        OsmTestUtils.createTestRelations(changesetId, nodeIds, wayIds);
      final Long[] relationIdsArr = relationIds.toArray(new Long[]{});

      final BoundingBox updatedBounds =
        OsmTestUtils.createAfterModifiedTestChangesetBounds();
      //Now update an existing changeset with a number of elements that, when combined with the
      //existing elements, are equal to the max allowed.  The elements should be written and the
      //changeset closed.
      Document responseData = null;
      try
      {
        responseData =
          resource()
            .path("api/0.6/changeset/" + changesetId + "/upload")
            .queryParam("mapId", "" + mapId)
            .type(MediaType.TEXT_XML)
            .accept(MediaType.TEXT_XML)
            .post(
              Document.class,
              "<osmChange version=\"0.3\" generator=\"iD\">" +
                "<create/>" +
                "<modify>" +
                  "<node id=\"" + nodeIdsArr[0] + "\" lon=\"" + updatedBounds.getMinLon() + "\" " +
                    "lat=\"" + updatedBounds.getMinLat() + "\" version=\"1\" changeset=\"" +
                    changesetId + "\">" +
                    "<tag k=\"key 1b\" v=\"val 1b\"></tag>" +
                    "<tag k=\"key 2b\" v=\"val 2b\"></tag>" +
                  "</node>" +
                  "<node id=\"" + nodeIdsArr[1] + "\" lon=\"" + originalBounds.getMaxLon() + "\" " +
                    "lat=\"" + updatedBounds.getMinLat() + "\" version=\"1\" changeset=\"" +
                    changesetId + "\">" +
                    "<tag k=\"key 3b\" v=\"val 3b\"></tag>" +
                  "</node>" +
                  "<way id=\"" + wayIdsArr[0] + "\" version=\"1\" changeset=\"" + changesetId + "\" >" +
                    "<nd ref=\"" + nodeIdsArr[0] + "\"></nd>" +
                    "<nd ref=\"" + nodeIdsArr[4] + "\"></nd>" +
                    "<tag k=\"key 2\" v=\"val 2\"></tag>" +
                  "</way>" +
                  "<way id=\"" + wayIdsArr[1] + "\" version=\"1\" changeset=\"" + changesetId + "\" >" +
                    "<nd ref=\"" + nodeIdsArr[4] + "\"></nd>" +
                    "<nd ref=\"" + nodeIdsArr[2] + "\"></nd>" +
                  "</way>" +
                  "<relation id=\"" + relationIdsArr[0] + "\" version=\"1\" changeset=\"" + changesetId + "\" >" +
                    "<member type=\"way\" role=\"role4\" ref=\"" + wayIdsArr[1] + "\"></member>" +
                    "<member type=\"way\" role=\"role2\" ref=\"" + wayIdsArr[0] + "\"></member>" +
                    "<member type=\"node\" ref=\"" + nodeIdsArr[2] + "\"></member>" +
                  "</relation>" +
                  "<relation id=\"" + relationIdsArr[1] + "\" version=\"1\" changeset=\"" + changesetId + "\" >" +
                    "<member type=\"relation\" role=\"role1\" ref=\"" + relationIdsArr[0] + "\"></member>" +
                    "<member type=\"node\" ref=\"" + nodeIdsArr[4] + "\"></member>" +
                    "<tag k=\"key 2\" v=\"val 2\"></tag>" +
                    "<tag k=\"key 3\" v=\"val 3\"></tag>" +
                  "</relation>" +
                "</modify>" +
                "<delete if-unused=\"true\"/>" +
              "</osmChange>");
      }
      catch (UniformInterfaceException e)
      {
        ClientResponse r = e.getResponse();
        Assert.fail("Unexpected response " + r.getStatus() + " " + r.getEntity(String.class));
      }
      Assert.assertNotNull(responseData);

      XPath xpath = XmlDocumentBuilder.createXPath();
      try
      {
        NodeList returnedNodes = XPathAPI.selectNodeList(responseData, "//osm/diffResult/node");
        Assert.assertEquals(2, returnedNodes.getLength());

        Assert.assertEquals(
          (long)nodeIdsArr[0],
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[1]/@old_id",
          responseData)));
        Assert.assertEquals(
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[1]/@old_id", responseData)),
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[1]/@new_id", responseData)));
        Assert.assertEquals(
          2, Long.parseLong(xpath.evaluate("//osm/diffResult/node[1]/@new_version", responseData)));

        Assert.assertEquals(
          (long)nodeIdsArr[1],
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[2]/@old_id",
          responseData)));
        Assert.assertEquals(
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[2]/@old_id", responseData)),
          Long.parseLong(xpath.evaluate("//osm/diffResult/node[2]/@new_id", responseData)));
        Assert.assertEquals(
          2, Long.parseLong(xpath.evaluate("//osm/diffResult/node[2]/@new_version", responseData)));

        NodeList returnedWays = XPathAPI.selectNodeList(responseData, "//osm/diffResult/way");
        Assert.assertEquals(2, returnedWays.getLength());

        Assert.assertEquals(
          (long)wayIdsArr[0],
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[1]/@old_id",
          responseData)));
        Assert.assertEquals(
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[1]/@old_id", responseData)),
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[1]/@new_id", responseData)));
        Assert.assertEquals(
          2, Long.parseLong(xpath.evaluate("//osm/diffResult/way[1]/@new_version", responseData)));

        Assert.assertEquals(
          (long)wayIdsArr[1],
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[2]/@old_id",
          responseData)));
        Assert.assertEquals(
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[2]/@old_id", responseData)),
          Long.parseLong(xpath.evaluate("//osm/diffResult/way[2]/@new_id", responseData)));
        Assert.assertEquals(
          2, Long.parseLong(xpath.evaluate("//osm/diffResult/way[2]/@new_version", responseData)));

        NodeList returnedRelations =
          XPathAPI.selectNodeList(responseData, "//osm/diffResult/relation");
        Assert.assertEquals(2, returnedRelations.getLength());

        //check the modified relations
        Assert.assertEquals(
          (long)relationIdsArr[0],
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[1]/@old_id",
          responseData)));
        Assert.assertEquals(
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[1]/@old_id", responseData)),
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[1]/@new_id", responseData)));
        Assert.assertEquals(
          2, Long.parseLong(xpath.evaluate("//osm/diffResult/relation[1]/@new_version", responseData)));

        Assert.assertEquals(
          (long)relationIdsArr[1],
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[2]/@old_id",
          responseData)));
        Assert.assertEquals(
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[2]/@old_id", responseData)),
          Long.parseLong(xpath.evaluate("//osm/diffResult/relation[2]/@new_id", responseData)));
        Assert.assertEquals(
          2, Long.parseLong(xpath.evaluate("//osm/diffResult/relation[2]/@new_version", responseData)));
      }
      catch (XPathExpressionException e)
      {
        Assert.fail("Error parsing response document: " + e.getMessage());
      }

      final Timestamp now = new Timestamp(Calendar.getInstance().getTimeInMillis());

      QChangesets changesets = QChangesets.changesets;
      hoot.services.db2.Changesets changeset =
      new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(changesets)
      .where(changesets.id.eq(changesetId)).singleResult(changesets);

      try
      {
      	 final Map<Long, CurrentNodes> nodes =

            		new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(currentNodesTbl)
            		.map(currentNodesTbl.id, currentNodesTbl);
        Assert.assertEquals(5, nodes.size());

        CurrentNodes nodeRecord = (CurrentNodes)nodes.get(nodeIdsArr[0]);
        Assert.assertEquals(new Long(changesetId), nodeRecord.getChangesetId());
        Assert.assertEquals(
          new Integer((int)(DbUtils.toDbCoordPrecision(updatedBounds.getMinLat()) *
            GeoUtils.GEO_RECORD_SCALE)),
          nodeRecord.getLatitude());
        Assert.assertEquals(
          new Integer((int)(DbUtils.toDbCoordPrecision(updatedBounds.getMinLon()) *
            GeoUtils.GEO_RECORD_SCALE)),
          nodeRecord.getLongitude());
        Assert.assertEquals(nodeIdsArr[0], nodeRecord.getId());
        Assert.assertEquals(
          new Long(QuadTileCalculator.tileForPoint(
            DbUtils.fromDbCoordValue(nodeRecord.getLatitude()),
            DbUtils.fromDbCoordValue(nodeRecord.getLongitude()))),
          nodeRecord.getTile());
        Thread.sleep(1000);
        Assert.assertTrue(nodeRecord.getTimestamp().before(now));
        Assert.assertEquals(new Long(2), nodeRecord.getVersion());
        Assert.assertEquals(new Boolean(true), nodeRecord.getVisible());
        Map<String, String> tags = PostgresUtils.postgresObjToHStore((PGobject)nodeRecord.getTags());
        Assert.assertNotNull(tags);
        Assert.assertEquals(2, tags.size());
        Assert.assertEquals("val 1b", tags.get("key 1b"));
        Assert.assertEquals("val 2b", tags.get("key 2b"));

        nodeRecord = (CurrentNodes)nodes.get(nodeIdsArr[1]);
        Assert.assertEquals(new Long(changesetId), nodeRecord.getChangesetId());
        Assert.assertEquals(
          new Integer((int)(DbUtils.toDbCoordPrecision(updatedBounds.getMinLat()) *
            GeoUtils.GEO_RECORD_SCALE)),
          nodeRecord.getLatitude());
        Assert.assertEquals(
          new Integer((int)(DbUtils.toDbCoordPrecision(originalBounds.getMaxLon()) *
            GeoUtils.GEO_RECORD_SCALE)),
          nodeRecord.getLongitude());
        Assert.assertEquals(nodeIdsArr[1], nodeRecord.getId());
        Assert.assertEquals(
          new Long(QuadTileCalculator.tileForPoint(
            DbUtils.fromDbCoordValue(nodeRecord.getLatitude()),
            DbUtils.fromDbCoordValue(nodeRecord.getLongitude()))),
          nodeRecord.getTile());
        Assert.assertTrue(nodeRecord.getTimestamp().before(now));
        Assert.assertEquals(new Long(2), nodeRecord.getVersion());
        Assert.assertEquals(new Boolean(true), nodeRecord.getVisible());
        tags = PostgresUtils.postgresObjToHStore((PGobject)nodeRecord.getTags());
        Assert.assertNotNull(tags);
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals("val 3b", tags.get("key 3b"));

        nodeRecord = (CurrentNodes)nodes.get(nodeIdsArr[3]);
        tags = PostgresUtils.postgresObjToHStore((PGobject)nodeRecord.getTags());
        Assert.assertNotNull(tags);
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals("val 3", tags.get("key 3"));

        nodeRecord = (CurrentNodes)nodes.get(nodeIdsArr[4]);
        tags = PostgresUtils.postgresObjToHStore((PGobject)nodeRecord.getTags());
        Assert.assertNotNull(tags);
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals("val 4", tags.get("key 4"));
      }
      catch (Exception e)
      {
        Assert.fail("Error checking nodes: " + e.getMessage());
      }

      try
      {
      	 final Map<Long, CurrentWays> ways =

            		new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(currentWaysTbl)
            		.map(currentWaysTbl.id, currentWaysTbl);
        Assert.assertEquals(3, ways.size());

        CurrentWays wayRecord = (CurrentWays)ways.get(wayIdsArr[0]);
        Assert.assertEquals(new Long(changesetId), wayRecord.getChangesetId());
        Assert.assertEquals(wayIdsArr[0], wayRecord.getId());
        Assert.assertTrue(wayRecord.getTimestamp().before(now));
        Assert.assertEquals(new Long(2), wayRecord.getVersion());
        Assert.assertEquals(new Boolean(true), wayRecord.getVisible());
        List<CurrentWayNodes> wayNodes =
        		new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(currentWayNodesTbl)
            .where(currentWayNodesTbl.wayId.eq(wayIdsArr[0]))
            .orderBy(currentWayNodesTbl.sequenceId.asc()).list(currentWayNodesTbl);
        Assert.assertEquals(2, wayNodes.size());
        CurrentWayNodes wayNode = wayNodes.get(0);
        Assert.assertEquals(nodeIdsArr[0], wayNode.getNodeId());
        Assert.assertEquals(new Long(1), wayNode.getSequenceId());
        Assert.assertEquals(wayRecord.getId(), wayNode.getWayId());
        wayNode = wayNodes.get(1);
        Assert.assertEquals(nodeIdsArr[4], wayNode.getNodeId());
        Assert.assertEquals(new Long(2), wayNode.getSequenceId());
        Assert.assertEquals(wayRecord.getId(), wayNode.getWayId());
        //verify the previously existing tags
        Map<String, String> tags = PostgresUtils.postgresObjToHStore((PGobject)wayRecord.getTags());
        Assert.assertNotNull(tags);
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals("val 2", tags.get("key 2"));

        wayRecord = (CurrentWays)ways.get(wayIdsArr[1]);
        Assert.assertEquals(new Long(changesetId), wayRecord.getChangesetId());
        Assert.assertEquals(wayIdsArr[1], wayRecord.getId());
        Assert.assertTrue(wayRecord.getTimestamp().before(now));
        Assert.assertEquals(new Long(2), wayRecord.getVersion());
        Assert.assertEquals(new Boolean(true), wayRecord.getVisible());
        wayNodes =
        		new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(currentWayNodesTbl)
            .where(currentWayNodesTbl.wayId.eq(wayIdsArr[1]))
            .orderBy(currentWayNodesTbl.sequenceId.asc()).list(currentWayNodesTbl);
        Assert.assertEquals(2, wayNodes.size());
        wayNode = wayNodes.get(0);
        Assert.assertEquals(nodeIdsArr[4], wayNode.getNodeId());
        Assert.assertEquals(new Long(1), wayNode.getSequenceId());
        Assert.assertEquals(wayRecord.getId(), wayNode.getWayId());
        wayNode = wayNodes.get(1);
        Assert.assertEquals(nodeIdsArr[2], wayNode.getNodeId());
        Assert.assertEquals(new Long(2), wayNode.getSequenceId());
        Assert.assertEquals(wayRecord.getId(), wayNode.getWayId());
        //verify the way with no tags
        Assert.assertTrue(
            wayRecord.getTags() == null ||
            StringUtils.isEmpty(((PGobject)wayRecord.getTags()).getValue()));

        //verify the created ways
        wayRecord = (CurrentWays)ways.get(wayIdsArr[2]);
        Assert.assertEquals(new Long(changesetId), wayRecord.getChangesetId());
        Assert.assertEquals(wayIdsArr[2], wayRecord.getId());
        Assert.assertTrue(wayRecord.getTimestamp().before(now));
        Assert.assertEquals(new Long(1), wayRecord.getVersion());
        Assert.assertEquals(new Boolean(true), wayRecord.getVisible());
        wayNodes =
        		new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(currentWayNodesTbl)
            .where(currentWayNodesTbl.wayId.eq(wayIdsArr[2]))
            .orderBy(currentWayNodesTbl.sequenceId.asc()).list(currentWayNodesTbl);
        Assert.assertEquals(2, wayNodes.size());
        wayNode = wayNodes.get(0);
        Assert.assertEquals(nodeIdsArr[0], wayNode.getNodeId());
        Assert.assertEquals(new Long(1), wayNode.getSequenceId());
        Assert.assertEquals(wayRecord.getId(), wayNode.getWayId());
        wayNode = wayNodes.get(1);
        Assert.assertEquals(nodeIdsArr[1], wayNode.getNodeId());
        Assert.assertEquals(new Long(2), wayNode.getSequenceId());
        Assert.assertEquals(wayRecord.getId(), wayNode.getWayId());
        //verify the created tags
        tags = PostgresUtils.postgresObjToHStore((PGobject)wayRecord.getTags());
        Assert.assertNotNull(tags);
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals("val 3", tags.get("key 3"));
      }
      catch (Exception e)
      {
        Assert.fail("Error checking ways: " + e.getMessage());
      }

      try
      {
      	final Map<Long, CurrentRelations> relations =

            		new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(currentRelationsTbl)
            		.map(currentRelationsTbl.id, currentRelationsTbl);
        Assert.assertEquals(4, relations.size());

        CurrentRelations relationRecord =
          (CurrentRelations)relations.get(relationIdsArr[0]);
        Assert.assertEquals(new Long(changesetId), relationRecord.getChangesetId());
        Assert.assertEquals(relationIdsArr[0], relationRecord.getId());
        Assert.assertTrue(relationRecord.getTimestamp().before(now));
        Assert.assertEquals(new Long(2), relationRecord.getVersion());
        Assert.assertEquals(new Boolean(true), relationRecord.getVisible());
        List<CurrentRelationMembers> members =
        		new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(currentRelationMembersTbl)
            .where(currentRelationMembersTbl.relationId.eq(relationIdsArr[0])
            	)
            .orderBy(currentRelationMembersTbl.sequenceId.asc())
            .list(currentRelationMembersTbl);
        Assert.assertEquals(3, members.size());
        CurrentRelationMembers member = members.get(0);
        Assert.assertEquals(
          relationRecord.getId(), member.getRelationId());
        Assert.assertEquals(DbUtils.nwr_enum.way, member.getMemberType());
        Assert.assertEquals("role4", member.getMemberRole());
        Assert.assertEquals(
          new Integer(1), member.getSequenceId());

        Assert.assertEquals(wayIdsArr[1], member.getMemberId());
        member = members.get(1);
        Assert.assertEquals(
          relationRecord.getId(), member.getRelationId());
        Assert.assertEquals(DbUtils.nwr_enum.way, member.getMemberType());
        Assert.assertEquals("role2", member.getMemberRole());
        Assert.assertEquals(
          new Integer(2), member.getSequenceId());

        Assert.assertEquals(wayIdsArr[0], member.getMemberId());
        member = members.get(2);
        Assert.assertEquals(
          relationRecord.getId(), member.getRelationId());
        Assert.assertEquals(DbUtils.nwr_enum.node, member.getMemberType());
        Assert.assertEquals("", member.getMemberRole());
        Assert.assertEquals(
          new Integer(3), member.getSequenceId());

        Assert.assertEquals(nodeIdsArr[2], member.getMemberId());
        Map<String, String> tags =
          PostgresUtils.postgresObjToHStore((PGobject)relationRecord.getTags());
        Assert.assertTrue(
          relationRecord.getTags() == null ||
          StringUtils.trimToNull(((PGobject)relationRecord.getTags()).getValue()) == null);

        relationRecord = (CurrentRelations)relations.get(relationIdsArr[1]);
        Assert.assertEquals(new Long(changesetId), relationRecord.getChangesetId());
        Assert.assertEquals(relationIdsArr[1], relationRecord.getId());
        Assert.assertTrue(relationRecord.getTimestamp().before(now));
        Assert.assertEquals(new Long(2), relationRecord.getVersion());
        Assert.assertEquals(new Boolean(true), relationRecord.getVisible());
        members =
        		new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(currentRelationMembersTbl)
            .where(currentRelationMembersTbl.relationId.eq(relationIdsArr[1])
            		)
            .orderBy(currentRelationMembersTbl.sequenceId.asc())
            .list(currentRelationMembersTbl);
        Assert.assertEquals(2, members.size());
        member = members.get(0);
        Assert.assertEquals(
          relationRecord.getId(), member.getRelationId());
        Assert.assertEquals(DbUtils.nwr_enum.relation, member.getMemberType());
        Assert.assertEquals("role1", member.getMemberRole());
        Assert.assertEquals(
          new Integer(1), member.getSequenceId());

        Assert.assertEquals(relationIdsArr[0], member.getMemberId());
        member = members.get(1);
        Assert.assertEquals(
          relationRecord.getId(), member.getRelationId());
        Assert.assertEquals(DbUtils.nwr_enum.node, member.getMemberType());
        Assert.assertEquals("", member.getMemberRole());
        Assert.assertEquals(
          new Integer(2), member.getSequenceId());

        Assert.assertEquals(nodeIdsArr[4], member.getMemberId());
        tags = PostgresUtils.postgresObjToHStore((PGobject)relationRecord.getTags());
        Assert.assertNotNull(tags);
        Assert.assertEquals(2, tags.size());
        Assert.assertEquals("val 2", tags.get("key 2"));
        Assert.assertEquals("val 3", tags.get("key 3"));

        relationRecord = (CurrentRelations)relations.get(relationIdsArr[2]);
        Assert.assertEquals(new Long(changesetId), relationRecord.getChangesetId());
        Assert.assertEquals(relationIdsArr[2], relationRecord.getId());
        Assert.assertTrue(relationRecord.getTimestamp().before(now));
        Assert.assertEquals(new Long(1), relationRecord.getVersion());
        Assert.assertEquals(new Boolean(true), relationRecord.getVisible());
        members =
        		new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(currentRelationMembersTbl)
            .where(currentRelationMembersTbl.relationId.eq(relationIdsArr[2])
            		)
            .orderBy(currentRelationMembersTbl.sequenceId.asc())
            .list(currentRelationMembersTbl);
        Assert.assertEquals(1, members.size());
        member = members.get(0);
        Assert.assertEquals(
          relationRecord.getId(), member.getRelationId());
        Assert.assertEquals(DbUtils.nwr_enum.way, member.getMemberType());
        Assert.assertEquals("", member.getMemberRole());
        Assert.assertEquals(
          new Integer(1), member.getSequenceId());

        Assert.assertEquals(wayIdsArr[1], member.getMemberId());
        tags = PostgresUtils.postgresObjToHStore((PGobject)relationRecord.getTags());
        Assert.assertNotNull(tags);
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals("val 4", tags.get("key 4"));

        relationRecord = (CurrentRelations)relations.get(relationIdsArr[3]);
        Assert.assertEquals(new Long(changesetId), relationRecord.getChangesetId());
        Assert.assertEquals(relationIdsArr[3], relationRecord.getId());
        Assert.assertTrue(relationRecord.getTimestamp().before(now));
        Assert.assertEquals(new Long(1), relationRecord.getVersion());
        Assert.assertEquals(new Boolean(true), relationRecord.getVisible());
        members =

        		new SQLQuery(conn, DbUtils.getConfiguration(mapId)).from(currentRelationMembersTbl)
            .where(currentRelationMembersTbl.relationId.eq(relationIdsArr[3])
            		)
            .orderBy(currentRelationMembersTbl.sequenceId.asc())
            .list(currentRelationMembersTbl);
        Assert.assertEquals(1, members.size());
        member = members.get(0);
        Assert.assertEquals(
          relationRecord.getId(), member.getRelationId());
        Assert.assertEquals(DbUtils.nwr_enum.node, member.getMemberType());
        Assert.assertEquals("role1", member.getMemberRole());
        Assert.assertEquals(
          new Integer(1), member.getSequenceId());

        Assert.assertEquals(nodeIdsArr[2], member.getMemberId());
        Assert.assertTrue(
            relationRecord.getTags() == null ||
            StringUtils.isEmpty(((PGobject)relationRecord.getTags()).getValue()));
      }
      catch (Exception e)
      {
        Assert.fail("Error checking relations: " + e.getMessage());
      }

      try
      {
        Assert.assertNotNull(changeset);
        Assert.assertTrue(changeset.getCreatedAt().before(now));
        Assert.assertTrue(changeset.getClosedAt().after(changeset.getCreatedAt()));
        Assert.assertTrue(changeset.getClosedAt().before(now));
        Assert.assertEquals(new Integer(18), changeset.getNumChanges());
        Assert.assertEquals(new Long(userId), changeset.getUserId());

        BoundingBox expandedBounds = new BoundingBox(originalBounds);
        expandedBounds.expand(
          updatedBounds,
          Double.parseDouble(HootProperties.getDefault("changesetBoundsExpansionFactorDeegrees")));
        hoot.services.models.osm.Changeset hootChangeset =
          new hoot.services.models.osm.Changeset(mapId, changesetId, conn);
        BoundingBox changesetBounds = hootChangeset.getBounds();
        Assert.assertTrue(changesetBounds.equals(expandedBounds));
      }
      catch (Exception e)
      {
        Assert.fail("Error checking changeset: " + e.getMessage());
      }
    }
    catch (Exception e)
    {
      log.error(e.getMessage());
      throw e;
    }
    finally
    {
      //set this back to default now that this test is over
      HootProperties.getInstance().setProperty(
        "maximumChangesetElements", HootProperties.getDefault("maximumChangesetElements"));
      Assert.assertEquals(
        Integer.parseInt(HootProperties.getDefault("maximumChangesetElements")),
        Integer.parseInt(HootProperties.getInstance().getProperty("maximumChangesetElements")));
    }
  }

  @Test(expected=UniformInterfaceException.class)
  @Category(UnitTest.class)
  public void testChangesetAutoCloseWhenNoElementsAddedToItBeforeExpiration() throws Exception
  {
    try
    {
      //A changeset is created, but no elements are added to it.  It should auto-close once
      //changesetIdleTimeoutMinutes time has passed, since that's when the changeset is set to
      //auto-close upon its creation.

      //set these props at the beginning, since they are read by
      //OsmResourceTestUtils.createTestChangeset
      Properties hootProps = HootProperties.getInstance();
      //Toggle the var that allows for testing changeset auto-closing.  This will change the service
      //to temporarily interpret changesetIdleTimeoutMinutes as a value in seconds instead of
      //minutes to enable a faster runtime for this test.
      hootProps.setProperty("testChangesetAutoClose", String.valueOf(true));
      //set the timeout to one second
      final int changesetIdleTimeoutSeconds = 1;
      hootProps.setProperty(
        "changesetIdleTimeoutMinutes", String.valueOf(changesetIdleTimeoutSeconds));
      HootProperties.setProperties(hootProps);
      Assert.assertEquals(
        true,
        Boolean.parseBoolean(HootProperties.getInstance().getProperty("testChangesetAutoClose")));
      Assert.assertEquals(
        changesetIdleTimeoutSeconds,
        Integer.parseInt(HootProperties.getInstance().getProperty("changesetIdleTimeoutMinutes")));

      final BoundingBox originalBounds = OsmTestUtils.createStartingTestBounds();
      final long changesetId = OsmTestUtils.createTestChangeset(originalBounds, 0);

      //pause long enough for the changeset to expire
      Thread.sleep(2000);

      //Access the changeset with a create request.  This will trigger closing the changeset, and
      //No data in the system should be modified by the create request.
      try
      {
        resource()
          .path("api/0.6/changeset/" + changesetId + "/upload")
          .queryParam("mapId", "" + mapId)
          .type(MediaType.TEXT_XML)
          .accept(MediaType.TEXT_XML)
          .post(
            Document.class,
            "<osmChange version=\"0.3\" generator=\"iD\">" +
              "<create>" +
                "<node id=\"-1\" lon=\"" + originalBounds.getMinLon() + "\" lat=\"" +
                  originalBounds.getMinLat() + "\" version=\"0\" changeset=\"" + changesetId + "\">" +
                  "<tag k=\"key 1\" v=\"val 1\"/>" +
                  "<tag k=\"key 2\" v=\"val 2\"/>" +
                "</node>" +
                "<node id=\"-2\" lon=\"" + originalBounds.getMaxLon() + "\" lat=\"" +
                  originalBounds.getMaxLat() + "\" version=\"0\" changeset=\"" + changesetId + "\">" +
                "</node>" +
              "</create>" +
              "<modify/>" +
              "<delete if-unused=\"true\"/>" +
            "</osmChange>");
      }
      catch (UniformInterfaceException e)
      {
        ClientResponse r = e.getResponse();
        Assert.assertEquals(Status.CONFLICT, Status.fromStatusCode(r.getStatus()));
        Assert.assertTrue(
          r.getEntity(String.class).contains(
            "The changeset with ID: " + changesetId + " was closed at"));

        OsmTestUtils.verifyTestChangesetClosed(changesetId, 0);

        throw e;
      }
    }
    catch (Exception e)
    {
      log.error(e.getMessage());
      throw e;
    }
    finally
    {
      //set these back to default now that this test is over
      HootProperties.getInstance().setProperty(
        "testChangesetAutoClose", HootProperties.getDefault("testChangesetAutoClose"));
      Assert.assertEquals(
        Boolean.parseBoolean(HootProperties.getDefault("testChangesetAutoClose")),
        Boolean.parseBoolean(HootProperties.getInstance().getProperty("testChangesetAutoClose")));
      HootProperties.getInstance().setProperty(
        "changesetIdleTimeoutMinutes", HootProperties.getDefault("changesetIdleTimeoutMinutes"));
      Assert.assertEquals(
        Integer.parseInt(HootProperties.getDefault("changesetIdleTimeoutMinutes")),
        Integer.parseInt(HootProperties.getInstance().getProperty("changesetIdleTimeoutMinutes")));
    }
  }

  @Test(expected=UniformInterfaceException.class)
  @Category(UnitTest.class)
  public void testChangesetAutoCloseWhenLengthBetweenUpdatesCausesExpiration() throws Exception
  {
    try
    {
      //A changeset is created and elements are added to it.  After that, a time longer than
      //changesetMaxOpenTimeHours passes before attempting to add any more elements to it.  A
      //changeset's expiration increases from  changesetIdleTimeoutMinutes to
      //changesetMaxOpenTimeHours after a single element is written to it.  The changeset should
      //auto-close.

      //set these props at the beginning, since they are read by
      //OsmResourceTestUtils.createTestChangeset
      Properties hootProps = HootProperties.getInstance();
      //Toggle the var that allows for testing changeset auto-closing.  This will change the service
      //to temporarily interpret changesetIdleTimeoutMinutes as a value in seconds instead of
      //minutes to enable a faster runtime for this test.
      hootProps.setProperty("testChangesetAutoClose", String.valueOf(true));
      //set the timeout to one second
      final int changesetIdleTimeoutSeconds = 1;
      hootProps.setProperty(
        "changesetIdleTimeoutMinutes", String.valueOf(changesetIdleTimeoutSeconds));
      final int changesetMaxOpenTimeSeconds = 2;
      hootProps.setProperty(
        "changesetMaxOpenTimeHours", String.valueOf(changesetMaxOpenTimeSeconds));
      HootProperties.setProperties(hootProps);
      Assert.assertEquals(
        true,
        Boolean.parseBoolean(HootProperties.getInstance().getProperty("testChangesetAutoClose")));
      Assert.assertEquals(
        changesetIdleTimeoutSeconds,
        Integer.parseInt(HootProperties.getInstance().getProperty("changesetIdleTimeoutMinutes")));
      Assert.assertEquals(
        changesetMaxOpenTimeSeconds,
        Integer.parseInt(HootProperties.getInstance().getProperty("changesetMaxOpenTimeHours")));

      final BoundingBox originalBounds = OsmTestUtils.createStartingTestBounds();
      final long changesetId = OsmTestUtils.createTestChangeset(originalBounds);
      final Set<Long> nodeIds =
        OsmTestUtils.createTestNodes(changesetId, originalBounds);
      final Long[] nodeIdsArr = nodeIds.toArray(new Long[]{});
      final Set<Long> wayIds =
        OsmTestUtils.createTestWays(changesetId, nodeIds);
      final Set<Long> relationIds =
        OsmTestUtils.createTestRelations(changesetId, nodeIds, wayIds);

      //pause long enough for the changeset to expire
      Thread.sleep(3000);

      final BoundingBox updateBounds =
        OsmTestUtils.createAfterModifiedTestChangesetBounds();
      //Access the changeset with an update request.  This will trigger closing the changeset, and
      //No data in the system should be modified by the update request.
      try
      {
        resource()
          .path("api/0.6/changeset/" + changesetId + "/upload")
          .queryParam("mapId", "" + mapId)
          .type(MediaType.TEXT_XML)
          .accept(MediaType.TEXT_XML)
          .post(
            Document.class,
            "<osmChange version=\"0.3\" generator=\"iD\">" +
              "<create/>" +
              "<modify>" +
                "<node id=\"" + nodeIdsArr[0] + "\" lon=\"" + updateBounds.getMinLon() + "\" " +
                  "lat=\"" + updateBounds.getMinLat() + "\" version=\"1\" changeset=\"" +
                  changesetId + "\">" +
                  "<tag k=\"key 1\" v=\"val 1\"></tag>" +
                  "<tag k=\"key 2\" v=\"val 2\"></tag>" +
                "</node>" +
              "</modify>" +
              "<delete if-unused=\"true\"/>" +
            "</osmChange>");
      }
      catch (UniformInterfaceException e)
      {
        ClientResponse r = e.getResponse();
        Assert.assertEquals(Status.CONFLICT, Status.fromStatusCode(r.getStatus()));
        Assert.assertTrue(
          r.getEntity(String.class).contains(
            "The changeset with ID: " + changesetId + " was closed at"));

        //make sure nothing was updated
        OsmTestUtils.verifyTestDataUnmodified(
          originalBounds, changesetId, nodeIds, wayIds, relationIds);
        OsmTestUtils.verifyTestChangesetClosed(changesetId);

        throw e;
      }
    }
    catch (Exception e)
    {
      log.error(e.getMessage());
      throw e;
    }
    finally
    {
      //set these back to default now that this test is over
      HootProperties.getInstance().setProperty(
        "testChangesetAutoClose", HootProperties.getDefault("testChangesetAutoClose"));
      Assert.assertEquals(
        Boolean.parseBoolean(HootProperties.getDefault("testChangesetAutoClose")),
        Boolean.parseBoolean(HootProperties.getInstance().getProperty("testChangesetAutoClose")));
      HootProperties.getInstance().setProperty(
        "changesetIdleTimeoutMinutes", HootProperties.getDefault("changesetIdleTimeoutMinutes"));
      Assert.assertEquals(
        Integer.parseInt(HootProperties.getDefault("changesetIdleTimeoutMinutes")),
        Integer.parseInt(HootProperties.getInstance().getProperty("changesetIdleTimeoutMinutes")));
      HootProperties.getInstance().setProperty(
        "changesetMaxOpenTimeHours", HootProperties.getDefault("changesetMaxOpenTimeHours"));
      Assert.assertEquals(
        Integer.parseInt(HootProperties.getDefault("changesetMaxOpenTimeHours")),
        Integer.parseInt(HootProperties.getInstance().getProperty("changesetMaxOpenTimeHours")));
    }
  }

  @Test
  @Category(UnitTest.class)
  public void testClosePreflight() throws Exception
  {
    try
    {
      String changeSetId = "1";
      resource()
        .path("api/0.6/changeset/" + changeSetId + "/close")
        .queryParam("mapId", "1")
        .options(String.class);
    }
    catch (UniformInterfaceException e)
    {
      ClientResponse r = e.getResponse();
      Assert.fail("Unexpected response " + r.getStatus() + " " + r.getEntity(String.class));
    }
    catch (Exception e)
    {
      log.error(e.getMessage());
      throw e;
    }
  }
}

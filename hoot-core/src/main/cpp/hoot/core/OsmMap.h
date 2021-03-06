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
 * @copyright Copyright (C) 2012, 2013, 2014, 2015 DigitalGlobe (http://www.digitalglobe.com/)
 */
#ifndef OSMMAP_H
#define OSMMAP_H

// Boost
#include <boost/enable_shared_from_this.hpp>

// GDAL Includes
#include <ogrsf_frmts.h>

// GEOS Includes
#include <geos/geom/Envelope.h>

// Hoot
#include <hoot/core/Units.h>
#include <hoot/core/elements/ElementId.h>
#include <hoot/core/elements/ElementProvider.h>
#include <hoot/core/elements/Node.h>
#include <hoot/core/elements/Relation.h>
#include <hoot/core/elements/Way.h>
#include <hoot/core/util/Log.h>
namespace hoot {
    namespace elements {
        class Tags;
    }
}

// Standard
#include <deque>
#include <vector>

// TGS
#include <tgs/RStarTree/HilbertRTree.h>

#include "DefaultIdGenerator.h"
#include "RelationMap.h"
#include "WayMap.h"


namespace hoot {

using namespace std;

class NodeFilter;
class OsmMapIndex;
class OsmMapListener;
class WayFilter;

/**
 * The OsmMap contains all the information necessary to represent an OSM map. It holds the nodes,
 * ways, relations and an index to access them efficiently. It also provides a number of methods
 * for CRUD.
 *
 * The OsmMap class is a beast. A bit too much of a beast. It would be nice to break it into smaller
 * chunks that are easier to maintain.
 *
 *  - For instance, complicated operations on the map such as recursively removing elements should
 *    live in another class. E.g. RecursiveElementRemover
 *  - Things like the filter operations can be replaced by visitors.
 *  - In the long term it might also be nice simplify the maintenance by merging all the elements
 *    into a single map and simplify the interface in a similar fashion.
 *  - I'd like to remove the OsmIndex circular reference, but I haven't figured out a good
 *    way to do that. Possibly refactor into an OsmMap class and OsmData class. The OsmMap class
 *    maintains pointers to OsmData and an OsmIndex where neither directly references the other. (?)
 */
class OsmMap : public enable_shared_from_this<OsmMap>, public ElementProvider
{
public:

  static string className() { return "hoot::OsmMap"; }

  typedef QHash<long, boost::shared_ptr<Node> > NodeMap;

  OsmMap();

  explicit OsmMap(shared_ptr<const OsmMap>);

  explicit OsmMap(shared_ptr<OsmMap>);

  explicit OsmMap(shared_ptr<OGRSpatialReference> srs);

  OsmMap(shared_ptr<const OsmMap>, shared_ptr<OGRSpatialReference> srs);

  ~OsmMap();

  void addElement(const shared_ptr<Element>& e);
  template<class T>
  void addElements(T it, T end);

  void addNode(const shared_ptr<Node>& n);

  void addRelation(const shared_ptr<Relation>& r);

  void addWay(const shared_ptr<Way>& w);

  /**
   * Calculates the bounds of the map by determining the extent of all the nodes. This is slow
   * every time and there is no caching.
   */
  OGREnvelope calculateBounds() const;

  /**
   * Similar to above, but it returns a geos Envelope.
   */
  geos::geom::Envelope calculateEnvelope() const;

  /**
   * This traverses all nodes and ways to calculate the maximum circular error. This is not cached
   * and has the potential to be quite expensive.
   */
  double calculateMaxCircularError() const;

  void clear();

  /**
   * Returns true if an element with the specified type/id exists.
   * Throws an exception if the type is unrecognized.
   */
  virtual bool containsElement(const ElementId& eid) const;
  bool containsElement(ElementType type, long id) const;
  bool containsElement(const shared_ptr<const Element>& e) const;

  /**
   * Returns true if the node is in this map.
   */
  bool containsNode(long id) const { return _nodes.find(id) != _nodes.end(); }

  bool containsRelation(long id) const { return _relations.find(id) != _relations.end(); }

  bool containsWay(long id) const { return _ways.find(id) != _ways.end(); }

  /**
   * Returns a copy of this map that only contains the specified ways. This can be handy when
   * performing what-if experiments.
   * @deprecated CopySubsetOp is now preferred.
   */
  shared_ptr<OsmMap> copyWays(const vector<long>& wIds) const;

  long createNextNodeId() const { return _idGen->createNodeId(); }

  long createNextRelationId() const { return _idGen->createRelationId(); }

  long createNextWayId() const { return _idGen->createWayId(); }

  std::vector<long> filterNodes(const NodeFilter& filter) const;

  std::vector<long> filterNodes(const NodeFilter& filter, const Coordinate& c,
                                Meters maxDistance) const;

  std::vector<long> filterWays(const WayFilter& filter) const;

  /**
   * Returns the ID of all the ways that are not filtered by filter and are within maxDistance
   * of "from".
   */
  std::vector<long> filterWays(const WayFilter& filter, shared_ptr<const Way> from,
                               Meters maxDistance, bool addError = false) const;

  /**
   * Returns a set of all element IDs that intersect with envelope e.
   */
  set<ElementId> findElements(const Envelope& e) const;

  /**
   * Does a very inefficient search for all neighbors within buffer distance. This should be
   * replaced by an index search at some point.
   */
  std::vector<long> findWayNeighbors(shared_ptr<const Way> way, Meters buffer) const;

  /**
   * Does a very inefficient search for all the ways that contain the given node.
   */
  std::vector<long> findWayByNode(long nodeId) const;

  /**
   * Searches for all ways with a tag that exactly matches the key and value. This is horribly
   * inefficient and appropriate mainly for testing.
   */
  std::vector<long> findWays(QString key, QString value) const;

  /**
   * Searches for all nodes with a tag that exactly matches the key and value. This is horribly
   * inefficient and appropriate mainly for testing.
   */
  std::vector<long> findNodes(QString key, QString value) const;

  virtual ConstElementPtr getElement(const ElementId& id) const;
  ConstElementPtr getElement(ElementType type, long id) const;
  ElementPtr getElement(const ElementId& id);
  ElementPtr getElement(ElementType type, long id);

  size_t getElementCount() const;

  const vector< shared_ptr<OsmMapListener> >& getListeners() const { return _listeners; }

  /**
   * This returns an index of the OsmMap. Adding or removing ways from the map will make the index
   * out of date and will require calling getIndex again.
   */
  const OsmMapIndex& getIndex() const { return *_index; }

  boost::shared_ptr<const Node> getNode(long id) const;

  const shared_ptr<Node>& getNode(long id);

  ConstNodePtr getNode(const ElementId& eid) const { return getNode(eid.getId()); }

  const NodePtr& getNode(const ElementId& eid) { return getNode(eid.getId()); }

  const NodeMap& getNodeMap() const { return _nodes; }

  /**
   * Returns the SRS for this map. The SRS should never be changed and defaults to WGS84.
   */
  boost::shared_ptr<OGRSpatialReference> getProjection() const { return _srs; }

  const shared_ptr<const Relation> getRelation(long id) const;

  const shared_ptr<Relation>& getRelation(long id);

  const RelationMap& getRelationMap() const { return _relations; }

  /**
   * Return the way with the specified id or null if it doesn't exist.
   */
  const shared_ptr<Way>& getWay(long id);
  const shared_ptr<Way>& getWay(ElementId eid);
  
  /**
   * Similar to above but const'd.
   *
   * We can't return these values by reference b/c the conversion from non-const to const requires
   * a copy. The copy would be a temporary variable if we returned a reference which creates some
   * weirdness and a warning.
   */
  const shared_ptr<const Way> getWay(long id) const;
  const shared_ptr<const Way> getWay(ElementId eid) const;

  const WayMap& getWays() const { return _ways; }

  static boost::shared_ptr<OGRSpatialReference> getWgs84();

  bool isEmpty() const { return _ways.size() == 0 && _nodes.size() == 0; }

  void registerListener(shared_ptr<OsmMapListener> l) { _listeners.push_back(l); }

  /**
   * Removes an element from the map. If the element exists as part of other elements it is
   * removed from those elements before being removed from the map.
   *
   * If this element contains children (e.g. multipolygon) the children will not be removed from
   * the map.
   *
   * If you would like to remove an element and all its children then see RecursiveElementRemover.
   */
  void removeElement(ElementId eid);

  /**
   * Removes an element from the map. No check is made before the removal, so removing an element
   * used by another Way or Relation will result in undefined behaviour.
   */
  void removeElementNoCheck(ElementType type, long id);

  /**
   * Remove the specified node from this map. A check will be made to make sure the node is not
   * part of any way before it is removed.
   */
  void removeNode(long nid);

  /**
   * Remove the specified node from this map. No check will be made to remove this node from ways.
   * If the node exists in one or more ways the results are undefined.
   */
  void removeNodeNoCheck(long nId);

  void removeRelation(const shared_ptr<Relation>& r) { removeRelation(r->getId()); }

  void removeRelation(long rId);

  /**
   * Remove the specified way from this map.
   */
  void removeWay(const shared_ptr<const Way>& w);

  void removeWay(long wId);

  /**
   * Removes the way from all relations and then removes the way from the map.
   */
  void removeWayFully(long wId);

  /**
   * Removes the way if isFiltered() == true.
   */
  void removeWays(const WayFilter& filter);

  /**
   * Replace the all instances of from with instances of to. In some cases this may be an invalid
   * operation and an exception will be throw. E.g. replacing a node with a way where the node
   * is part of another way.
   */
  void replace(const shared_ptr<const Element>& from, const shared_ptr<Element>& to);

  /**
   * Intelligently replaces all instances of oldNode with newNode. This looks at all the ways
   * for references to oldNode and replaces those references with newNode. Finally, oldNode is
   * removed from this OsmMap entirely.
   */
  void replaceNode(long oldId, long newId);

  /**
   * Resets the way and node counters. This should ONLY BE CALLED BY UNIT TESTS.
   */
  static void resetCounters() { IdGenerator::getInstance()->reset(); }

  void setIdGenerator(shared_ptr<IdGenerator> gen) { _idGenSp = gen; _idGen = gen.get(); }

  void setProjection(shared_ptr<OGRSpatialReference> srs);

  /**
   * Validates the consistency of the map. Primarily this checks to make sure that all nodes
   * referenced by a way exist in the map. A full dump of all invalid ways is logged before the
   * function throws an error.
   * @param strict If true, the method throws an exception rather than returning a result if the
   *               validation fails.
   * @return True if the map is valid, false otherwise.
   */
  bool validate(bool strict = true) const;

  /**
   * Calls the visitRo method on all elements. See Element::visitRo for a more thorough description.
   *  - The order will always be nodes, ways, relations, but the IDs will not be in any specific
   *    order.
   *  - Unlike Element::visitRo, elements will not be visited multiple times.
   *  - Modifying the OsmMap while traversing will result in undefined behaviour.
   *  - This should be slightly faster than visitRw.
   *
   * If the visitor implements OsmMapConsumer then setOsmMap will be called before visiting any
   * elements.
   */
  void visitRo(ElementVisitor& visitor) const;

  /**
   * Calls the visitRw method on all elements. See Element::visitRw for a more thorough description.
   *  - The order will always be nodes, ways, relations, but the IDs will not be in any specific
   *    order.
   *  - Elements that are added during the traversal may or may not be visited.
   *  - Elements may be deleted during traversal.
   *  - The visitor is guaranteed to not visit deleted elements.
   *
   * If the visitor implements OsmMapConsumer then setOsmMap will be called before visiting any
   * elements.
   */
  void visitRw(ElementVisitor& visitor);

protected:

  mutable IdGenerator* _idGen;
  mutable shared_ptr<IdGenerator> _idGenSp;

  static shared_ptr<OGRSpatialReference> _wgs84;

  boost::shared_ptr<OGRSpatialReference> _srs;

  mutable NodeMap _nodes;
  mutable RelationMap _relations;
  mutable WayMap _ways;

  shared_ptr<OsmMapIndex> _index;
  shared_ptr<Relation> _nullRelation;
  shared_ptr<Way> _nullWay;
  shared_ptr<const Way> _constNullWay;
  RelationMap::iterator _tmpRelationIt;
  mutable WayMap::const_iterator _tmpWayIt;
  std::vector< shared_ptr<OsmMapListener> > _listeners;

  vector< shared_ptr<Element> > _replaceTmpArray;

  void _copy(boost::shared_ptr<const OsmMap> from);

  void _replaceNodeInRelations(long oldId, long newId);

};

typedef boost::shared_ptr<OsmMap> OsmMapPtr;
typedef boost::shared_ptr<const OsmMap> ConstOsmMapPtr;
// ConstOsmMapPtrR - The R is for reference.
typedef const ConstOsmMapPtr& ConstOsmMapPtrR;

template<class T>
void addElements(T it, T end)
{
  while (it != end)
  {
    addElement(*it);
    ++it;
  }
}

inline const shared_ptr<Node>& OsmMap::getNode(long id)
{
  NodeMap::Iterator it = _nodes.find(id);
  if (it == _nodes.end())
  {
    LOG_ERROR("Requested an invalid node ID " << id);
    assert(false);
  }
  return it.value();
}

inline const shared_ptr<const Relation> OsmMap::getRelation(long id) const
{
  RelationMap::iterator it = _relations.find(id);
  if (it != _relations.end())
  {
    return it->second;
  }
  else
  {
    return _nullRelation;
  }
}

inline const shared_ptr<Relation>& OsmMap::getRelation(long id)
{
  _tmpRelationIt = _relations.find(id);
  if (_tmpRelationIt != _relations.end())
  {
    return _tmpRelationIt->second;
  }
  else
  {
    return _nullRelation;
  }
}

inline const shared_ptr<const Way> OsmMap::getWay(long id) const
{
  _tmpWayIt = _ways.find(id);
  if (_tmpWayIt != _ways.end())
  {
    return _tmpWayIt->second;
  }
  else
  {
    return _constNullWay;
  }
}

inline const shared_ptr<const Way> OsmMap::getWay(ElementId eid) const
{
  assert(eid.getType() == ElementType::Way);
  return getWay(eid.getId());
}

inline const shared_ptr<Way>& OsmMap::getWay(long id)
{
  _tmpWayIt = _ways.find(id);
  if (_tmpWayIt != _ways.end())
  {
    return _tmpWayIt->second;
  }
  else
  {
    return _nullWay;
  }
}

inline const shared_ptr<Way>& OsmMap::getWay(ElementId eid)
{
  assert(eid.getType() == ElementType::Way);
  return getWay(eid.getId());
}

}

#endif // OSMMAP_H

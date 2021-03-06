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

#ifndef INTERSECTIONSPLITTER_H
#define INTERSECTIONSPLITTER_H

// Hoot
#include <hoot/core/OsmMap.h>
#include <hoot/core/ops/OsmMapOperation.h>

// Qt
#include <QMultiHash>
#include <QSet>

namespace hoot
{

/**
 * Given an OsmMap intersection splitter makes all intersections contain only way end nodes. For
 * example, if two ways make a four way intesection the intersection splitter will convert that
 * into four ways that meet at a four way intersection. No nodes are modified in this process.
 *
 *   - OsmSchema::isHighway or OsmSchema::isLinearWaterway must return true for a way to be
 *     considered for splitting.
 */
class IntersectionSplitter : public OsmMapOperation
{
public:

  static string className() { return "hoot::IntersectionSplitter"; }

  IntersectionSplitter();

  IntersectionSplitter(shared_ptr<OsmMap> map);

  void apply(shared_ptr<OsmMap>& map);

  static void splitIntersections(shared_ptr<OsmMap> map);

  void splitIntersections();

private:
  shared_ptr<OsmMap> _map;
  QMultiHash<long, long> _nodeToWays;
  QSet<long> _todoNodes;

  void _mapNodesToWays();

  void _mapNodesToWay(shared_ptr<Way> w);

  void _removeWayFromMap(shared_ptr<Way> way);

  /**
   * Given a way and a node, split the way at that node.
   */
  void _splitWay(long wayId, long nodeId);
};

}

#endif // INTERSECTIONSPLITTER_H

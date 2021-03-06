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
 * @copyright Copyright (C) 2012, 2013, 2014 DigitalGlobe (http://www.digitalglobe.com/)
 */

#include "DividedHighwayMerger.h"

// Boost
using namespace boost;

// GEOS
#include <geos/geom/GeometryFactory.h>
#include <geos/geom/LineString.h>
#include <geos/geom/Point.h>
#include <geos/operation/distance/DistanceOp.h>
using namespace geos::geom;
using namespace geos::operation::distance;

// Hoot
#include <hoot/core/OsmMap.h>
#include <hoot/core/WorkingMap.h>
#include <hoot/core/algorithms/DirectionFinder.h>
#include <hoot/core/algorithms/LineStringAverager.h>
#include <hoot/core/elements/Node.h>
#include <hoot/core/elements/Way.h>
#include <hoot/core/filters/OneWayFilter.h>
#include <hoot/core/filters/ParallelWayFilter.h>
#include <hoot/core/filters/StatusFilter.h>
#include <hoot/core/filters/UnknownFilter.h>
#include <hoot/core/filters/WayDirectionFilter.h>
#include <hoot/core/filters/WayBufferFilter.h>
#include <hoot/core/index/OsmMapIndex.h>
#include <hoot/core/util/ElementConverter.h>
#include <hoot/core/util/Log.h>
using namespace hoot::elements;

// Qt
#include <qdebug.h>

// Standard
#include <iostream>
using namespace std;

// Tgs
#include <tgs/StreamUtils.h>
using namespace Tgs;

#include "DividedHighwayManipulation.h"

#define SQR(x) ((x) * (x))

namespace hoot
{

DividedHighwayMerger::DividedHighwayMerger(Meters minSeparation, Meters maxSeparation,
                                           Meters vectorError, double matchPercent)
{
  _minSeparation = minSeparation;
  _maxSeparation = maxSeparation;
  _vectorError = vectorError;
  _matchPercent = matchPercent;

  shared_ptr<OneWayFilter> owFilter(new OneWayFilter());
  shared_ptr<UnknownFilter> unknownFilter(new UnknownFilter());
  _oneWayUnknownFilter.addFilter(owFilter);
  _oneWayUnknownFilter.addFilter(unknownFilter);
}

const vector< shared_ptr<Manipulation> >& DividedHighwayMerger::findAllManipulations(
        shared_ptr<const OsmMap> map)
{
  // go through all the oneway, unknown ways
  vector<long> oneWays = map->filterWays(_oneWayUnknownFilter);

  // return the result
  return findWayManipulations(map, oneWays);
}

const vector< shared_ptr<Manipulation> >& DividedHighwayMerger::findWayManipulations(
        shared_ptr<const OsmMap> map, const vector<long>& wids)
{
  _result.clear();
  _map = map;

  for (size_t i = 0; i < wids.size(); i++)
  {
    if (Log::getInstance().isInfoEnabled())
    {
      printf("DividedHighwayMerger::findAllManipulations() %d   \r", (int)_result.size());
      fflush(stdout);
    }
    // evaluate the way to see if it is a divided highway candidate and add it to _result.
    _findMatches(wids[i]);
  }
  if (Log::getInstance().isInfoEnabled())
  {
    printf("\n");
  }

  // return the result
  return _result;
}

vector<long> DividedHighwayMerger::_findCenterWays(shared_ptr<const Way> w1,
                                                   shared_ptr<const Way> w2)
{
  shared_ptr<OneWayFilter> owFilter(new OneWayFilter(false));

  Status s;
  if (w1->getStatus() == Status::Unknown1)
  {
    s = Status::Unknown2;
  }
  else
  {
    s = Status::Unknown1;
  }

  shared_ptr<StatusFilter> statusFilter(new StatusFilter(s));
  shared_ptr<ParallelWayFilter> parallelFilter(new ParallelWayFilter(_map, w1));

  ElementConverter ec(_map);
  shared_ptr<LineString> ls2 = ec.convertToLineString(w2);
  if (DirectionFinder::isSimilarDirection(_map, w1, w2) == false)
  {
    ls2.reset(dynamic_cast<LineString*>(ls2->reverse()));
  }

  // calculate the center line of two ways.
  shared_ptr<LineString> center = LineStringAverager::average(
    ec.convertToLineString(w1), ls2);
  shared_ptr<WayBufferFilter> distanceFilter(new WayBufferFilter(_map, center, 0.0,
    (w1->getCircularError() + w2->getCircularError()) / 2.0, _matchPercent));
  WayFilterChain filter;

  filter.addFilter(owFilter);
  filter.addFilter(statusFilter);
  filter.addFilter(parallelFilter);
  filter.addFilter(distanceFilter);

  return _map->filterWays(filter);
}

void DividedHighwayMerger::_findMatches(long baseWayId)
{
  shared_ptr<const Way> baseWay = _map->getWay(baseWayId);
  // find all the parallel and opposite ways that could be candidates.
  vector<long> otherWays = _findOtherWays(baseWay);

  //qDebug() << "otherWays.size()" << otherWays.size();

  // go through all the other ways
  for (size_t oi = 0; oi < otherWays.size(); oi++)
  {
    // this ensures that we'll only test a pair of ways once.
    if (baseWay->getId() < otherWays[oi])
    {
      shared_ptr<const Way> otherWay = _map->getWay(otherWays[oi]);

      // find all potential center lines
      vector<long> centerWays = _findCenterWays(baseWay, otherWay);

      // for each center line
      for (size_t ci = 0; ci < centerWays.size(); ci++)
      {
        // create a new manipulation and add it onto the result.
        shared_ptr<Manipulation> m(new DividedHighwayManipulation(baseWay->getId(),
          otherWay->getId(), centerWays[ci], _map, _vectorError));
        _result.push_back(m);
        //printf("DividedHighwayMerger::_findMatches() %d   \n", (int)_result.size());
        //fflush(stdout);
      }
    }
  }
}

vector<long> DividedHighwayMerger::_findOtherWays(boost::shared_ptr<const hoot::Way> baseWay)
{
  shared_ptr<OneWayFilter> owFilter(new OneWayFilter());
  shared_ptr<StatusFilter> statusFilter(new StatusFilter(baseWay->getStatus()));

  shared_ptr<WayBufferFilter> distanceFilter(new WayBufferFilter(_map, baseWay,
    _maxSeparation, _matchPercent));
  shared_ptr<ParallelWayFilter> parallelFilter(new ParallelWayFilter(_map, baseWay));
  shared_ptr<WayDirectionFilter> directionFilter(new WayDirectionFilter(_map, baseWay, false));
  WayFilterChain filter;

  filter.addFilter(owFilter);
  filter.addFilter(statusFilter);
  filter.addFilter(distanceFilter);
  filter.addFilter(parallelFilter);
  filter.addFilter(directionFilter);

  return _map->filterWays(filter);
}

}




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
 * @copyright Copyright (C) 2013, 2014 DigitalGlobe (http://www.digitalglobe.com/)
 */
package hoot.services.geo;

/**
 * Various geospatial utilities for hoot services
 */
public class GeoUtils
{
  public static final double DEFAULT_COORD_VALUE = -181.0;
  
  /**
   * coordinates are stored in the databse as integers and this factor is used for converting them 
   * to and from decimals
   */
  public static final int GEO_RECORD_SCALE = 10000000;
  
  /**
   * Determines if a pair of coordinates lie within world boundaries
   * 
   * @param lat latitude coordinate
   * @param lon longitude coordinate
   * @return true if both coordinates lie within world boundaries; false otherwise
   */
  public static boolean coordsInWorld(final double lat, final double lon)
  {
    return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
  }
}

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
 * @copyright Copyright (C) 2013 DigitalGlobe (http://www.digitalglobe.com/)
 */

#ifndef __TGS__RANDOM_H__
#define __TGS__RANDOM_H__

// Standard Includes
#include <stdlib.h>
#include <vector>

#include "../TgsExport.h"

namespace Tgs
{
  class TGS_EXPORT Random
  {
  public:
    static bool coinToss();

    static double generateGaussian(double mean, double sigma);

    static double generateUniform();

    /**
     * Randomizes the elements in the specified vector
     */
    template<class _T>
    static void randomizeVector(std::vector<_T>& v)
    {
      for (unsigned int i = 0; i < v.size() * 2; i++)
      {
        int index1 = rand() % v.size();
        int index2 = rand() % v.size();
        std::swap(v[index1], v[index2]);
      }
    }
  };
}

#endif

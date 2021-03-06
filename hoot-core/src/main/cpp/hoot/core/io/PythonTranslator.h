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
#ifndef PYTHONTRANSLATOR_H
#define PYTHONTRANSLATOR_H

#include <hoot/core/io/ScriptTranslator.h>
#include <hoot/core/io/ScriptToOgrTranslator.h>


namespace hoot
{

class PythonTranslator : public ScriptTranslator
    //, public ScriptToOgrTranslator
{
public:

  static std::string className() { return "hoot::PythonTranslator"; }

  PythonTranslator();

  virtual ~PythonTranslator();

  virtual bool isValidScript();

  /**
   * don't evaluate this first.
   */
  virtual int order() const { return 100; }

protected:
  // avoid including Python.h in this header file.
  void* _translateFunction;

  virtual void _init();

  virtual void _finalize();

  virtual void _translateToOsm(Tags& t, const char *layerName);
};

}

#endif // PYTHONTRANSLATOR_H

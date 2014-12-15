package com.lfv.lanzius.server;

import java.util.Iterator;
import javax.swing.DefaultListModel;
import org.jdom.Document;
import org.jdom.Element;

/**
 * <p>
 * DocumentListModel
 * <p>
 * Copyright &copy; LFV 2007, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2.0 (Lanzius)
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
public class DocumentListModel extends DefaultListModel {

    public DocumentListModel(Document doc, String section, String prefix, String defaultName) {
        synchronized(doc) {
            Element e = doc.getRootElement();
            if(e!=null) {
                e = e.getChild(section);
                if(e!=null) {
                    Iterator iter = e.getChildren().iterator();
                    while(iter.hasNext()) {
                        e = (Element)iter.next();
                        addElement(new DocumentModelItem(e, prefix, defaultName));
                    }
                }
            }
        }
    }
}

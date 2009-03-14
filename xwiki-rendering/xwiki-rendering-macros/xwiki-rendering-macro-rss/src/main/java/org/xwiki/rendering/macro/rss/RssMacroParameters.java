/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xwiki.rendering.macro.rss;

import java.net.URL;

import org.apache.commons.lang.StringUtils;
import org.xwiki.rendering.macro.descriptor.ParameterDescription;
import org.xwiki.rendering.macro.descriptor.ParameterMandatory;

/**
 * Parameters for the {@link org.xwiki.rendering.internal.macro.rss.RssMacro} Macro.
 * 
 * @version $Id: $
 * @since 1.8RC1
 */
public class RssMacroParameters
{

    /**
     * The URL of the RSS feed.
     */
    private String feed;

    /**
     * If "true" displays a summary in addition to the feed item link.
     */
    private boolean full;

    /**
     * The number of feed items to display.
     */
    private int count = Integer.MAX_VALUE;

    /**
     * If "true" and if the feed has an image, display it.
     */
    private boolean image;

    /**
     * The width of the enclosing box containing the RSS macro output.
     */
    private String width = StringUtils.EMPTY;

    /**
     * If "true" then adds class id elements (rssitem, rssitemtitle, rssitemdescription, rsschanneltitle, etc) which you
     * can style by modifying your skin's CSS file.
     */
    private boolean css = true;

    /**
     * The RSS feed URL.
     */
    private URL feedURL;

    /**
     * @return the RSS feed URL.
     */
    public String getFeed()
    {
        return feed;
    }

    /**
     * @param feed the RSS feed URL.
     * @throws java.net.MalformedURLException if the URL is malformed.
     */
    @ParameterMandatory
    @ParameterDescription("URL of the RSS feed")
    public void setFeed(String feed) throws java.net.MalformedURLException
    {
        this.feed = feed;
        this.feedURL = new java.net.URL(feed);
    }

    /**
     * @param image whether to display the feed's image.
     */
    @ParameterDescription("If the feeds has an image associated, display it?")
    public void setImage(boolean image)
    {
        this.image = image;
    }

    /**
     * @return whether to display the feed's image.
     */
    public boolean isImage()
    {
        return image;
    }

    /**
     * @param width the width of the RSS box, that will dismiss potential CSS rules defining its default value.
     */
    @ParameterDescription("The width, in px or %, of the box containing the RSS output (default is 30%)")
    public void setWidth(String width)
    {
        this.width = width;
    }

    /**
     * @return the width of the RSS box, that will dismiss potential CSS rules defining its default value.
     */
    public String getWidth()
    {
        return this.width;
    }

    /**
     * @param css whether to add class id elements (rssitem, rssitemtitle, rssitemdescription, rsschanneltitle, etc).
     */
    @ParameterDescription("Specifies whether to add class id elements (rssitem, rssitemtitle, rssitemdescription etc).")
    public void setCss(boolean css)
    {
        this.css = css;
    }

    /**
     * @return whether to add class id elements (rssitem, rssitemtitle, rssitemdescription, rsschanneltitle, etc).
     */
    public boolean isCss()
    {
        return this.css;
    }

    /**
     * @param count the number of feed items to display.
     */
    @ParameterDescription("The maximum number of feed items to display on the page.")
    public void setCount(int count)
    {
        this.count = count;
    }

    /**
     * @return the number of feed items to display.
     */
    public int getCount()
    {
        return count;
    }

    /**
     * @return the feed's URL
     */
    public URL getFeedURL()
    {
        return feedURL;
    }

    /**
     * @param full if "true" displays a summary in addition to the feed item link.
     */
    @ParameterDescription("Display full entries")
    public void setFull(boolean full)
    {
        this.full = full;
    }

    /**
     * @return if "true" displays a summary in addition to the feed item link
     */
    public boolean isFull()
    {
        return full;
    }
}

package com.droidfeed.data.parser

import android.util.Xml
import com.droidfeed.data.model.Post
import com.droidfeed.data.model.Source
import com.droidfeed.util.logStackTrace
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsXmlParser @Inject constructor(
    private val rssParser: RssParser,
    private val feedParser: FeedParser
) {

    /**
     * Parses given RSS or Atom XML into a list of articles.
     *
     * @param xml as string
     * @return list of articles
     */
    fun parse(xml: String, source: Source): List<Post> {
        val inputStream = StringReader(xml)

        inputStream.use {
            val parser = Xml.newPullParser()

            try {
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(inputStream)
                parser.nextTag()
            } catch (e: XmlPullParserException) {
                logStackTrace(e)
            }

            return when (parser.name) {
                "rss" -> rssParser.parseArticles(parser,source)
                "feed" -> feedParser.parseArticles(parser,source)
                else -> listOf()
            }
        }
    }
}
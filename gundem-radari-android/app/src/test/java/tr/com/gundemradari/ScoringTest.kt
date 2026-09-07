package tr.com.gundemradari
import org.junit.Assert.*
import org.junit.Test
import tr.com.gundemradari.data.SourceEntity
import tr.com.gundemradari.scan.similarity
import tr.com.gundemradari.scan.scores
class ScoringTest { private val official=SourceEntity("x","X","official","","rss",1.0,true,false,"")
 @Test fun importantNewsScoresHigher(){assertTrue(scores("Merkez bankası faiz kararını açıkladı",official).first>=50)}
 @Test fun titleSimilarityFindsSameEvent(){assertTrue(similarity("İzmir açıklarında deprem", "İzmir açıklarında büyük deprem meydana geldi")>.5)}
}
